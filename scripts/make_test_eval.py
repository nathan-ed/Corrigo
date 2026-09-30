#!/usr/bin/env python3
"""Fictional evaluation for testing PDF4Teachers: no real student, name or data.
Copies are identified by NATO code words only (ALPHA, BRAVO...)."""
import os, sys, yaml
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

ROOT = sys.argv[1]
W, H = A4
GRID_W, GRID_H = 165400, 233900
BLUE = (0.12, 0.25, 0.75)

def gx(x): return int(x / W * GRID_W)
def gy(y_top): return int(y_top / H * GRID_H)

# Each exercise: answers by method, and mistakes
EX1 = {  # f(x) = (2x+1)^3
    'chain': ["f'(x) = 3(2x+1)^2 · 2", "      = 6(2x+1)^2"],
    'chain_forgot2': ["f'(x) = 3(2x+1)^2"],
    'expand': ["(2x+1)^3 = 8x^3 + 12x^2 + 6x + 1", "f'(x) = 24x^2 + 24x + 6"],
}
EX2 = {  # u1 = 3, r = 4
    'formula': ["u20 = u1 + 19r = 3 + 19·4 = 79", "S20 = 20·(3+79)/2 = 820"],
    'formula_n': ["u20 = u1 + 20r = 3 + 80 = 83", "S20 = 20·(3+83)/2 = 860"],
    'listing': ["3, 7, 11, 15, 19, 23, ... , 79", "S20 = 3+7+11+...+79 = 820"],
}
EX3 = {  # x^2 - 5x + 6 = 0
    'delta': ["Delta = 25 - 24 = 1", "x = (5 ± 1)/2  donc x = 2 ou x = 3"],
    'delta_sign': ["Delta = 25 - 24 = 1", "x = (-5 ± 1)/2  donc x = -2 ou x = -3"],
    'factor': ["x^2 - 5x + 6 = (x-2)(x-3) = 0", "donc x = 2 ou x = 3"],
}
COPIES = [
    ('01_ALPHA',   'chain',         'formula',   'delta',      [3, 2, 2, 3]),
    ('02_BRAVO',   'expand',        'listing',   'factor',     [3, 2, 1.5, 3]),
    ('03_CHARLIE', 'chain_forgot2', 'formula_n', 'delta',      [1.5, 1, 1, 3]),
    ('04_DELTA',   'chain',         'formula',   'delta_sign', [3, 2, 2, 1.5]),
    ('05_ECHO',    'expand',        'formula_n', 'factor',     [3, 1, 1, 3]),
    ('06_FOXTROT', 'chain_forgot2', 'listing',   'delta_sign', [1.5, 2, 1.5, 1.5]),
    ('07_GOLF',    'chain',         'formula',   'factor',     [3, 2, 2, 3]),
    ('08_HOTEL',   'expand',        'formula',   'delta',      [3, 2, 2, 3]),
]
COMMENTS = {  # comment text by answer variant: placed right of the answer
    'chain_forgot2': "Attention : dérivée de 2x+1 !",
    'formula_n': "u_n = u_1 + (n-1)r",
    'delta_sign': "Signe de b !",
    'listing': "Juste, mais long : utiliser la formule du terme général",
    'expand': "Juste, mais la dérivation d'une composée est plus rapide",
}

def header(c, page, title=None):
    c.setFont('Helvetica', 9); c.setFillColorRGB(0.3, 0.3, 0.3)
    c.drawString(50, H - 40, 'Collège Exemple — test fictif'); c.drawRightString(W - 50, H - 40, f'Page {page} sur 4')
    c.line(50, H - 46, W - 50, H - 46)
    c.setFillColorRGB(0, 0, 0)
    if title:
        c.setFont('Helvetica-Bold', 13); c.drawString(50, H - 80, title)

def answer(c, lines, y_top):
    c.setFont('Helvetica-Oblique', 13); c.setFillColorRGB(*BLUE)
    for i, line in enumerate(lines):
        c.drawString(70, H - (y_top + i * 24), line)
    c.setFillColorRGB(0, 0, 0)

def make_copy(folder, name, m1, m2, m3, scores, with_comments=True):
    path = os.path.join(folder, name + '.pdf')
    c = canvas.Canvas(path, pagesize=A4)
    code = name.split('_', 1)[1]
    # Page 1: cover
    header(c, 1)
    c.setFont('Helvetica-Bold', 20); c.drawCentredString(W / 2, H - 120, 'Test fictif de mathématiques')
    c.setFont('Helvetica', 13); c.drawCentredString(W / 2, H - 145, 'Dérivées, suites, équations')
    c.roundRect(60, H - 205, W - 120, 36, 6)
    c.drawString(75, H - 192, 'Copie :'); c.setFont('Helvetica-Oblique', 15); c.setFillColorRGB(*BLUE); c.drawString(140, H - 193, code)
    c.setFillColorRGB(0, 0, 0); c.setFont('Helvetica', 11)
    rows = [('Exercice', 'Points', 'Obtenus'), ('Ex 1', '3', ''), ('Ex 2 a', '2', ''), ('Ex 2 b', '2', ''), ('Ex 3', '3', ''), ('Total', '10', '')]
    for i, row in enumerate(rows):
        y = H - 260 - i * 26
        c.rect(150, y - 8, 300, 26)
        c.drawString(160, y, row[0]); c.drawString(290, y, row[1]); c.drawString(370, y, row[2])
    # Page 2: Ex 1
    c.showPage(); header(c, 2, 'Exercice 1  [3 points]')
    c.setFont('Helvetica', 12); c.drawString(50, H - 105, 'Calculer la dérivée de f(x) = (2x + 1)^3.')
    answer(c, EX1[m1], 150)
    # Page 3: Ex 2
    c.showPage(); header(c, 3, 'Exercice 2  [4 points]')
    c.setFont('Helvetica', 12)
    c.drawString(50, H - 105, 'Soit (u_n) la suite arithmétique de premier terme u_1 = 3 et de raison r = 4.')
    c.drawString(50, H - 125, 'a) Calculer u_20.     b) Calculer la somme S_20 des 20 premiers termes.')
    answer(c, EX2[m2][:1], 175); answer(c, EX2[m2][1:], 320)
    # Page 4: Ex 3
    c.showPage(); header(c, 4, 'Exercice 3  [3 points]')
    c.setFont('Helvetica', 12); c.drawString(50, H - 105, 'Résoudre l\'équation x^2 - 5x + 6 = 0.')
    answer(c, EX3[m3], 150)
    c.save()

    # Edition (grades and comments), stored next to the PDF
    grades = [  # Totals in the table of page 0, sub-grades next to the answers
        dict(x=gx(420), y=gy(560), page=0, index=0, parentPath='', value=float(sum(scores)), total=10.0, outOfTotal=-1.0, name='Total', alwaysVisible=True),
        dict(x=gx(470), y=gy(140), page=1, index=0, parentPath=r'\Total', value=float(scores[0]), total=3.0, outOfTotal=-1.0, name='Ex 1', alwaysVisible=True),
        dict(x=gx(370), y=gy(328), page=0, index=1, parentPath=r'\Total', value=float(scores[1] + scores[2]), total=4.0, outOfTotal=-1.0, name='Ex 2', alwaysVisible=True),
        dict(x=gx(470), y=gy(165), page=2, index=0, parentPath=r'\Total\Ex 2', value=float(scores[1]), total=2.0, outOfTotal=-1.0, name='a', alwaysVisible=True),
        dict(x=gx(470), y=gy(310), page=2, index=1, parentPath=r'\Total\Ex 2', value=float(scores[2]), total=2.0, outOfTotal=-1.0, name='b', alwaysVisible=True),
        dict(x=gx(470), y=gy(140), page=3, index=2, parentPath=r'\Total', value=float(scores[3]), total=3.0, outOfTotal=-1.0, name='Ex 3', alwaysVisible=True),
    ]
    texts = {}
    def add_text(page, x, y_top, text, color='0xd32f2fff'):
        texts.setdefault(f'page{page}', []).append(dict(x=gx(x), y=gy(y_top), color=color, font='Open Sans', size=14.0,
                                                          bold=False, italic=False, text=text, maxWidth=40.0))
    if with_comments:
        for page, variant, y in [(1, m1, 175), (2, m2, 190), (3, m3, 175)]:
            if variant in COMMENTS: add_text(page, 330, y, COMMENTS[variant])
        if scores == [3, 2, 2, 3]: add_text(0, 380, 600, 'Très bien !', '0x2e7d32ff')
    edition = dict(versionID=14, lastScrollValue=0.0, skills={}, images={}, vectors={}, texts=texts, grades=grades)
    with open(os.path.join(folder, '.' + name + '.pdf.yml'), 'w') as f:
        yaml.safe_dump(edition, f, allow_unicode=True, sort_keys=False)

class_a = os.path.join(ROOT, 'Classe-A', 'Test-1')
class_b = os.path.join(ROOT, 'Classe-B', 'Test-1')
os.makedirs(class_a, exist_ok=True); os.makedirs(class_b, exist_ok=True)
for copy in COPIES:
    make_copy(class_a, *copy)
for copy in [('01_INDIA', 'expand', 'formula_n', 'delta', [3, 1, 1, 3]), ('02_JULIETT', 'chain', 'listing', 'factor', [3, 2, 1.5, 3])]:
    make_copy(class_b, *copy)
print('ok', class_a, class_b)
