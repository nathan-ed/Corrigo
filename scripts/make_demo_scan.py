#!/usr/bin/env python3
# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Fictional evaluation for the demo videos and the screenshots of the user guide: no real student, name or data.

The students are named after mathematicians. The copies are handwritten (a handwriting font, slightly tilted) and
scanned (rendered as images, on off-white paper), all in one PDF like the scan of a class.

    make_demo_scan.py scan OUT.pdf [--lang en|fr]
        The scan of all the copies, in class order (for "New evaluation…").
    make_demo_scan.py graded DIR [--lang en|fr] [--graded N]
        The copies split in DIR (NN_NAME.pdf), the grade scale on each, the first N graded: points, comments,
        methods and mistakes (for the class overview and the previews).

Needs reportlab, Pillow, pdftoppm and img2pdf, and a handwriting font (Kalam, OFL) downloaded in ~/.cache/corrigo-demo.
"""
import argparse, os, random, subprocess, tempfile, uuid, yaml, io
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image, ImageFilter

W, H = A4
GRID_W, GRID_H = 165400, 233900
INK = (0.10, 0.20, 0.62)
FONT_DIR = os.path.expanduser('~/.cache/corrigo-demo')
FONT_URL = 'https://github.com/google/fonts/raw/main/ofl/kalam/Kalam-Regular.ttf'

STUDENTS = ['Ada', 'Blaise', 'Carl', 'Emmy', 'Évariste', 'Hypatia', 'Isaac', 'Leonhard', 'Maryam', 'Pierre', 'Sophie', 'Srinivasa']

T = {
    'en': dict(school='Demo school — fictional test', page='Page {0} of 4', title='Mathematics test', subtitle='Derivatives, sequences, equations',
               name='Name:', exercise='Exercise', points='Points', obtained='Obtained', total='Total',
               ex1='Exercise 1  [3 points]', ex1q='Find the derivative of f(x) = (2x + 1)³.',
               ex2='Exercise 2  [4 points]', ex2q='(u_n) is the arithmetic sequence with first term u_1 = 3 and common difference r = 4.',
               ex2q2='a) Find u_20.      b) Find the sum S_20 of the first 20 terms.',
               ex3='Exercise 3  [3 points]', ex3q='Solve the equation x² - 5x + 6 = 0.', so='so', or_='or'),
    'fr': dict(school='École démo — test fictif', page='Page {0} sur 4', title='Test de mathématiques', subtitle='Dérivées, suites, équations',
               name='Nom :', exercise='Exercice', points='Points', obtained='Obtenus', total='Total',
               ex1='Exercice 1  [3 points]', ex1q='Calculer la dérivée de f(x) = (2x + 1)³.',
               ex2='Exercice 2  [4 points]', ex2q='Soit (u_n) la suite arithmétique de premier terme u_1 = 3 et de raison r = 4.',
               ex2q2='a) Calculer u_20.      b) Calculer la somme S_20 des 20 premiers termes.',
               ex3='Exercice 3  [3 points]', ex3q='Résoudre l\'équation x² - 5x + 6 = 0.', so='donc', or_='ou'),
}

# Answers by method or mistake, in the order of the students: (Ex 1, Ex 2, Ex 3)
EX1 = {
    'chain': ["f'(x) = 3(2x+1)^2 · 2", "       = 6(2x+1)^2"],
    'chain_forgot': ["f'(x) = 3(2x+1)^2"],
    'expand': ["(2x+1)^3 = 8x^3 + 12x^2 + 6x + 1", "f'(x) = 24x^2 + 24x + 6"],
}
EX2 = {
    'formula': (["u_20 = u_1 + 19r = 3 + 19·4 = 79"], ["S_20 = 20·(3 + 79)/2 = 820"]),
    'formula_n': (["u_20 = u_1 + 20r = 3 + 80 = 83"], ["S_20 = 20·(3 + 83)/2 = 860"]),
    'listing': (["3, 7, 11, 15, 19, ... , 79"], ["S_20 = 3 + 7 + 11 + ... + 79 = 820"]),
}
EX3 = {
    'delta': ["Δ = 25 - 24 = 1", "x = (5 ± 1)/2  {so}  x = 2  {or_}  x = 3"],
    'delta_sign': ["Δ = 25 - 24 = 1", "x = (-5 ± 1)/2  {so}  x = -2  {or_}  x = -3"],
    'factor': ["x^2 - 5x + 6 = (x - 2)(x - 3) = 0", "{so}  x = 2  {or_}  x = 3"],
}
ANSWERS = [
    ('chain', 'formula', 'delta'), ('expand', 'listing', 'factor'), ('chain_forgot', 'formula_n', 'delta'),
    ('chain', 'formula', 'delta_sign'), ('expand', 'formula_n', 'factor'), ('chain_forgot', 'listing', 'delta_sign'),
    ('chain', 'formula', 'factor'), ('expand', 'formula', 'delta'), ('chain', 'formula_n', 'delta'),
    ('chain_forgot', 'formula', 'factor'), ('chain', 'listing', 'delta'), ('expand', 'formula', 'delta_sign'),
]
# Where the answers are, from the top of the page (points)
Y_EX1, Y_EX2A, Y_EX2B, Y_EX3 = 160, 190, 330, 160


def font():
    path = os.path.join(FONT_DIR, 'Kalam-Regular.ttf')
    if not os.path.exists(path):
        os.makedirs(FONT_DIR, exist_ok=True)
        subprocess.run(['curl', '-sSfL', '-o', path, FONT_URL], check=True)
    pdfmetrics.registerFont(TTFont('Hand', path))


def hand(c, x, y_top, text, rng, size=17):
    """Handwritten line: a handwriting font, slightly moved and tilted; characters it lacks (Δ…) in a plain font."""
    text = text.replace('^2', '²').replace('^3', '³')
    c.saveState()
    c.setFillColorRGB(*INK)
    c.translate(x + rng.uniform(-4, 4), H - y_top + rng.uniform(-2, 2))
    c.rotate(rng.uniform(-1.4, 1.4))
    size += rng.uniform(-0.8, 0.8)
    face = pdfmetrics.getFont('Hand').face
    cursor = 0
    for char in text:
        name = 'Hand' if ord(char) in face.charToGlyph else 'Helvetica'
        c.setFont(name, size if name == 'Hand' else size * 0.8)
        c.drawString(cursor, 0, char)
        cursor += pdfmetrics.stringWidth(char, name, size if name == 'Hand' else size * 0.8)
    c.restoreState()


def header(c, t, page, title=None):
    c.setFont('Helvetica', 9); c.setFillColorRGB(0.3, 0.3, 0.3)
    c.drawString(50, H - 40, t['school']); c.drawRightString(W - 50, H - 40, t['page'].format(page))
    c.line(50, H - 46, W - 50, H - 46)
    c.setFillColorRGB(0, 0, 0)
    if title:
        c.setFont('Helvetica-Bold', 13); c.drawString(50, H - 80, title)


def draw_copy(c, t, student, answers, rng):
    m1, m2, m3 = answers
    header(c, t, 1)
    c.setFont('Helvetica-Bold', 20); c.drawCentredString(W / 2, H - 120, t['title'])
    c.setFont('Helvetica', 13); c.drawCentredString(W / 2, H - 145, t['subtitle'])
    c.roundRect(60, H - 210, W - 120, 40, 6)
    c.drawString(75, H - 194, t['name'])
    hand(c, 140, 196, student, rng, 22)
    c.setFont('Helvetica', 11)
    rows = [(t['exercise'], t['points'], t['obtained']), ('Ex 1', '3', ''), ('Ex 2', '4', ''), ('Ex 3', '3', ''), (t['total'], '10', '')]
    for i, row in enumerate(rows):
        y = H - 265 - i * 26
        c.rect(150, y - 8, 300, 26)
        c.drawString(160, y, row[0]); c.drawString(290, y, row[1]); c.drawString(370, y, row[2])
    c.showPage()
    header(c, t, 2, t['ex1']); c.setFont('Helvetica', 12); c.drawString(50, H - 105, t['ex1q'])
    for i, line in enumerate(EX1[m1]): hand(c, 70, Y_EX1 + i * 30, line, rng)
    c.showPage()
    header(c, t, 3, t['ex2']); c.setFont('Helvetica', 12); c.drawString(50, H - 105, t['ex2q']); c.drawString(50, H - 125, t['ex2q2'])
    a, b = EX2[m2]
    for i, line in enumerate(a): hand(c, 70, Y_EX2A + i * 30, line, rng)
    for i, line in enumerate(b): hand(c, 70, Y_EX2B + i * 30, line, rng)
    c.showPage()
    header(c, t, 4, t['ex3']); c.setFont('Helvetica', 12); c.drawString(50, H - 105, t['ex3q'])
    for i, line in enumerate(EX3[m3]): hand(c, 70, Y_EX3 + i * 30, line.format(so=t["so"], or_=t["or_"]), rng)
    c.showPage()


def scanned(vector_pdf, out_pdf, seed):
    """Renders the pages as a scanner would: grey-ish paper, a slight tilt, JPEG."""
    rng = random.Random(seed)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(['pdftoppm', '-r', '130', '-png', vector_pdf, os.path.join(tmp, 'p')], check=True)
        pages = sorted(f for f in os.listdir(tmp) if f.endswith('.png'))
        jpgs = []
        for name in pages:
            image = Image.open(os.path.join(tmp, name)).convert('RGB')
            paper = Image.new('RGB', image.size, (244, 242, 236))
            image = Image.composite(image, paper, image.convert('L').point(lambda v: 255 - v))
            image = image.rotate(rng.uniform(-0.5, 0.5), resample=Image.BICUBIC, fillcolor=(244, 242, 236))
            image = image.filter(ImageFilter.GaussianBlur(0.35))
            path = os.path.join(tmp, name[:-4] + '.jpg')
            image.save(path, quality=72)
            jpgs.append(path)
        with open(out_pdf, 'wb') as f:
            f.write(subprocess.run(['img2pdf', '--pagesize', 'A4', *jpgs], check=True, capture_output=True).stdout)


def file_name(index, student):
    return f'{index + 1:02d}_{student.upper()}.pdf'


def make_scan(out, lang, only=None):
    font()
    t = T[lang]
    with tempfile.TemporaryDirectory() as tmp:
        vector = os.path.join(tmp, 'vector.pdf')
        c = canvas.Canvas(vector, pagesize=A4)
        for i, student in enumerate(STUDENTS):
            if only is not None and i != only: continue
            draw_copy(c, t, student, ANSWERS[i], random.Random(i))
        c.save()
        scanned(vector, out, only if only is not None else 0)


# THE GRADED EVALUATION

TAGS = {  # key: (exercise, kind, name en, name fr, points, grade)
    'chain': ('Ex 1', 'METHOD', 'Chain rule', 'Dérivation d\'une composée', None, None),
    'expand': ('Ex 1', 'METHOD', 'Expanded first', 'Développé d\'abord', None, None),
    'chain_forgot': ('Ex 1', 'MISTAKE', 'Inner derivative forgotten', 'Facteur u′ oublié', 1.0, None),
    'formula': ('Ex 2', 'METHOD', 'General term formula', 'Formule du terme général', None, None),
    'listing': ('Ex 2', 'METHOD', 'Listed the terms', 'Liste des termes', None, None),
    'formula_n': ('Ex 2', 'MISTAKE', 'n instead of n − 1', 'n au lieu de n − 1', 1.0, 'a'),
    'delta': ('Ex 3', 'METHOD', 'Discriminant', 'Discriminant', None, None),
    'factor': ('Ex 3', 'METHOD', 'Factorisation', 'Factorisation', None, None),
    'delta_sign': ('Ex 3', 'MISTAKE', 'Sign of b', 'Signe de b', 1.5, None),
}
COMMENTS = {
    'en': {'expand': 'Right, but the chain rule is faster', 'listing': 'Right, but long: use the formula',
           'formula_n': 'u_n = u_1 + (n − 1)r', 'delta_sign': 'x = (−b ± √Δ)/2a', 'good': 'Very good!'},
    'fr': {'expand': 'Juste, mais la dérivation d\'une composée est plus rapide', 'listing': 'Juste, mais long : utiliser la formule du terme général',
           'formula_n': 'u_n = u_1 + (n − 1)r', 'delta_sign': 'x = (−b ± √Δ)/2a', 'good': 'Très bien !'},
}


def gx(x): return int(x / W * GRID_W)
def gy(y_top): return int(y_top / H * GRID_H)


def make_graded(folder, lang, graded):
    os.makedirs(folder, exist_ok=True)
    tags = {key: str(uuid.UUID(int=random.Random(key).getrandbits(128))) for key in TAGS}
    copies_tags = {}
    for i, student in enumerate(STUDENTS):
        name = file_name(i, student)
        make_scan(os.path.join(folder, name), lang, only=i)
        m1, m2, m3 = ANSWERS[i]
        done = i < graded
        # Points: full, minus the mistakes
        ex1 = 3 - (1 if m1 == 'chain_forgot' else 0)
        ex2a = 2 - (1 if m2 == 'formula_n' else 0)
        ex2b = 2 - (0.5 if m2 == 'formula_n' else 0)
        ex3 = 3 - (1.5 if m3 == 'delta_sign' else 0)
        value = lambda v: float(v) if done else -1.0
        grades = [
            dict(x=gx(385), y=gy(360), page=0, index=0, parentPath='', value=value(ex1 + ex2a + ex2b + ex3), total=10.0, outOfTotal=-1.0, name='Total', alwaysVisible=True),
            dict(x=gx(470), y=gy(Y_EX1 + 75), page=1, index=0, parentPath=r'\Total', value=value(ex1), total=3.0, outOfTotal=-1.0, name='Ex 1', alwaysVisible=True),
            dict(x=gx(470), y=gy(80), page=2, index=1, parentPath=r'\Total', value=value(ex2a + ex2b), total=4.0, outOfTotal=-1.0, name='Ex 2', alwaysVisible=True),
            dict(x=gx(470), y=gy(Y_EX2A + 45), page=2, index=0, parentPath=r'\Total\Ex 2', value=value(ex2a), total=2.0, outOfTotal=-1.0, name='a', alwaysVisible=True),
            dict(x=gx(470), y=gy(Y_EX2B + 45), page=2, index=1, parentPath=r'\Total\Ex 2', value=value(ex2b), total=2.0, outOfTotal=-1.0, name='b', alwaysVisible=True),
            dict(x=gx(470), y=gy(Y_EX3 + 75), page=3, index=2, parentPath=r'\Total', value=value(ex3), total=3.0, outOfTotal=-1.0, name='Ex 3', alwaysVisible=True),
        ]
        texts = {}
        def comment(page, y_top, text, grade, color='0xd32f2fff'):
            texts.setdefault(f'page{page}', []).append(dict(x=gx(330), y=gy(y_top), color=color, font='Open Sans', size=14.0, bold=False,
                                                              italic=False, text=text, maxWidth=30.0, gradeComment=grade))
        if done:
            c = COMMENTS[lang]
            if m1 == 'expand': comment(1, Y_EX1 + 80, c['expand'], r'\Total\Ex 1')
            if m2 == 'listing': comment(2, Y_EX2B + 50, c['listing'], r'\Total\Ex 2\b')
            if m2 == 'formula_n': comment(2, Y_EX2A + 50, c['formula_n'], r'\Total\Ex 2\a')
            if m3 == 'delta_sign': comment(3, Y_EX3 + 80, c['delta_sign'], r'\Total\Ex 3')
            uses = []
            for key, y in ((m1, Y_EX1), (m2, Y_EX2A), (m3, Y_EX3 + 30)):
                exercise, kind, en, fr, points, sub = TAGS[key]
                page = {'Ex 1': 1, 'Ex 2': 2, 'Ex 3': 3}[exercise]
                grade = '\\Total\\' + exercise + ('\\' + sub if sub else ('\\a' if exercise == 'Ex 2' else ''))
                uses.append(dict(id=str(uuid.UUID(int=random.Random(name + key).getrandbits(128))), tag=tags[key], exercise=exercise,
                                 grade=grade, page=page, x=float(gx(170)), y=float(gy(y - 4)),
                                 labelX=float(gx(390)), labelY=float(gy(y - 14))))  # The pill right of the answer, as a teacher drags it
            copies_tags[name] = uses
        edition = dict(versionID=14, lastScrollValue=0.0, skills={}, images={}, vectors={}, texts=texts, grades=grades)
        with open(os.path.join(folder, '.' + name + '.yml'), 'w') as f:
            yaml.safe_dump(edition, f, allow_unicode=True, sort_keys=False)
    data = os.path.join(folder, '.pdf4teachers')
    os.makedirs(data, exist_ok=True)
    tag_list = []
    for key, (exercise, kind, en, fr, points, sub) in TAGS.items():
        tag = dict(id=tags[key], exercise=exercise, name=en if lang == 'en' else fr, kind=kind)
        if points: tag['points'] = points
        if points and sub: tag['gradePoints'] = {'\\Total\\' + exercise + '\\' + sub: points}
        tag_list.append(tag)
    with open(os.path.join(data, 'tags.yml'), 'w') as f:
        yaml.safe_dump(dict(tags=tag_list, copies=copies_tags), f, allow_unicode=True, sort_keys=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['scan', 'graded'])
    parser.add_argument('out')
    parser.add_argument('--lang', choices=['en', 'fr'], default='en')
    parser.add_argument('--graded', type=int, default=9)
    args = parser.parse_args()
    if args.mode == 'scan':
        make_scan(args.out, args.lang)
    else:
        make_graded(args.out, args.lang, args.graded)
    print('ok', args.out)
