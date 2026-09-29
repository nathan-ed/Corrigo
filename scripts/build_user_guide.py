#!/usr/bin/env python3
"""Builds the user guides of the application from the Markdown guides of docs/.

    python3 scripts/build_user_guide.py

For each language, writes in src/main/resources/translations/:
  <language>.html  one page with an outline on the side, opened in the browser by Help → Load documentation
  <language>.pdf   the same guide to print

Needs the Python "markdown" package, and Chromium (or Google Chrome) for the PDF.
"""
import html as htmllib, os, re, shutil, subprocess, sys, tempfile, unicodedata
import markdown

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUIDES = {  # language file -> folder of the guide, title, label of the outline
    'en_us': ('docs/user-guide', 'User guide', 'Contents'),
    'fr_fr': ('docs/guide-utilisateur', "Guide d'utilisation", 'Sommaire'),
}
OUTPUT = os.path.join(ROOT, 'src/main/resources/translations')

CSS = """
:root { --text: #1c1c1c; --muted: #5f6b7a; --accent: #1565c0; --title: #0d47a1; --bg: #ffffff; --side: #f4f6f9;
        --border: #d9dee5; --code: #eef2f7; --th: #e7f0fc; }
@media (prefers-color-scheme: dark) {
  :root { --text: #e6e6e6; --muted: #a0a8b3; --accent: #64a8ff; --title: #8cc0ff; --bg: #1e1f22; --side: #26282c;
          --border: #3a3d42; --code: #2c2f34; --th: #243246; }
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; scroll-padding-top: 16px; }
body { margin: 0; background: var(--bg); color: var(--text); font-family: 'Open Sans', 'Segoe UI', 'DejaVu Sans', sans-serif;
       font-size: 15px; line-height: 1.55; }
nav { position: fixed; top: 0; left: 0; bottom: 0; width: 290px; overflow-y: auto; background: var(--side);
      border-right: 1px solid var(--border); padding: 20px 16px 40px 20px; font-size: 14px; }
nav .title { font-weight: 700; font-size: 17px; color: var(--title); margin-bottom: 14px; }
nav ol { list-style: none; padding: 0; margin: 0; }
nav li { margin: 0; }
nav > ol > li { margin-top: 10px; }
nav > ol > li > a { font-weight: 600; color: var(--text); }
nav ol ol { margin: 3px 0 0 10px; border-left: 2px solid var(--border); padding-left: 10px; }
nav ol ol a { color: var(--muted); display: block; padding: 1px 0; }
nav a { text-decoration: none; }
nav a:hover { color: var(--accent); }
nav a.current { color: var(--accent); font-weight: 600; }
main { margin-left: 290px; padding: 28px 48px 80px 48px; max-width: 980px; }
h1 { font-size: 28px; color: var(--accent); border-bottom: 2px solid var(--accent); padding-bottom: 6px; margin: 36px 0 12px 0; }
section.chapter:first-of-type h1 { margin-top: 0; }
h2 { font-size: 20px; color: var(--title); margin: 28px 0 8px 0; }
h3 { font-size: 16px; margin: 20px 0 6px 0; }
a { color: var(--accent); }
code { font-family: 'DejaVu Sans Mono', Consolas, monospace; font-size: 0.86em; background: var(--code); padding: 1px 5px; border-radius: 4px; }
pre { background: var(--code); padding: 12px; border-radius: 6px; overflow-x: auto; }
pre code { background: none; padding: 0; }
table { border-collapse: collapse; margin: 10px 0; }
th, td { border: 1px solid var(--border); padding: 5px 10px; text-align: left; vertical-align: top; }
th { background: var(--th); }
li { margin: 3px 0; }
@media (max-width: 900px) { nav { position: static; width: auto; border-right: none; border-bottom: 1px solid var(--border); }
                            main { margin-left: 0; padding: 20px; } }
@media print {
  @page { size: A4; margin: 18mm 17mm 20mm 17mm; }
  :root { --text: #1c1c1c; --muted: #5f6b7a; --accent: #1565c0; --title: #0d47a1; --bg: #fff; --code: #eef2f7; --th: #e7f0fc; --border: #c9d3de; }
  nav { display: none; }
  main { margin: 0; padding: 0; max-width: none; }
  body { font-size: 10.5pt; }
  section.chapter { break-before: page; }
  section.chapter:first-of-type { break-before: auto; }
  h1 { margin-top: 0; }
  h2, h3 { break-after: avoid; }
  table, pre { break-inside: avoid; }
}
"""

# Highlights the part of the outline being read
SCRIPT = """
const links = [...document.querySelectorAll('nav a')];
const targets = links.map(a => document.getElementById(a.getAttribute('href').slice(1))).filter(Boolean);
function update(){
  let current = targets[0];
  for(const t of targets){ if(t.getBoundingClientRect().top < 80) current = t; }
  links.forEach(a => a.classList.toggle('current', current && a.getAttribute('href') === '#' + current.id));
}
document.addEventListener('scroll', update, {passive: true});
update();
"""


def slug(text):
    """GitHub-like anchor of a heading."""
    text = htmllib.unescape(re.sub(r'<[^>]+>', '', text)).strip().lower()
    text = ''.join(c for c in text if unicodedata.category(c)[0] in 'LN' or c in ' -_')
    return text.replace(' ', '-')


def chapter_id(file):
    return 'chapter-' + os.path.splitext(file)[0]


def build(language, folder, title, contents):
    folder = os.path.join(ROOT, folder)
    files = ['README.md'] + sorted(f for f in os.listdir(folder) if re.match(r'\d\d-.*\.md$', f))
    sections, outline = [], []
    for file in files:
        text = open(os.path.join(folder, file), encoding='utf-8').read()
        # Links to the other language are not part of this guide
        text = re.sub(r'^\*[^*\n]*\]\(\.\./[^)]*\)\.?\*\s*$', '', text, flags=re.M)
        prefix = chapter_id(file)

        def link(match):
            label, target = match.group(1), match.group(2)
            if target.startswith('http'):
                return match.group(0)
            name, _, anchor = target.partition('#')
            name = name or file
            return f'[{label}](#{chapter_id(name)}{"--" + anchor if anchor else ""})'
        text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link, text)
        page = markdown.markdown(text, extensions=['tables', 'fenced_code', 'sane_lists'])

        # Ids of the headings, unique in the whole guide; the outline lists the chapters and their sections
        chapter = {'title': None, 'id': None, 'sections': []}

        def heading(m):
            level, content = m.group(1), m.group(2)
            anchor = f'{prefix}--{slug(content)}'
            plain = htmllib.escape(htmllib.unescape(re.sub(r'<[^>]+>', '', content)))
            if level == 'h1' and chapter['title'] is None:
                chapter['title'], chapter['id'] = plain, anchor
            elif level == 'h2':
                chapter['sections'].append((plain, anchor))
            return f'<{level} id="{anchor}">{content}</{level}>'
        page = re.sub(r'<(h[1-6])>(.*?)</\1>', heading, page)
        outline.append(chapter)
        sections.append(f'<section class="chapter" id="{prefix}">{page}</section>')

    body = ''.join(sections)
    ids = set(re.findall(r' id="([^"]+)"', body))
    missing = sorted(set(re.findall(r'href="#([^"]+)"', body)) - ids)
    if missing:
        sys.exit(f'{language}: links without target: {missing}')

    nav = [f'<div class="title">{htmllib.escape(title)}</div><ol>']
    for chapter in outline:
        nav.append(f'<li><a href="#{chapter["id"]}">{chapter["title"]}</a>')
        if chapter['sections']:
            nav.append('<ol>' + ''.join(f'<li><a href="#{a}">{t}</a></li>' for t, a in chapter['sections']) + '</ol>')
        nav.append('</li>')
    nav.append('</ol>')
    document = (f'<!doctype html><html lang="{language[:2]}"><head><meta charset="utf-8">'
                f'<meta name="viewport" content="width=device-width, initial-scale=1"><title>{htmllib.escape(title)}</title>'
                f'<style>{CSS}</style></head><body><nav aria-label="{htmllib.escape(contents)}">{"".join(nav)}</nav>'
                f'<main>{body}</main><script>{SCRIPT}</script></body></html>')

    html_target = os.path.join(OUTPUT, language + '.html')
    open(html_target, 'w', encoding='utf-8').write(document)
    print(f'{html_target}: {os.path.getsize(html_target) // 1024} kB')

    browser = shutil.which('chromium') or shutil.which('chromium-browser') or shutil.which('google-chrome')
    if not browser:
        sys.exit('Chromium or Google Chrome is needed to render the PDF')
    pdf_target = os.path.join(OUTPUT, language + '.pdf')
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([browser, '--headless', '--disable-gpu', '--no-pdf-header-footer', '--user-data-dir=' + tmp,
                        '--print-to-pdf=' + pdf_target, 'file://' + html_target],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f'{pdf_target}: {os.path.getsize(pdf_target) // 1024} kB')


if __name__ == '__main__':
    for language, (folder, title, contents) in GUIDES.items():
        build(language, folder, title, contents)
