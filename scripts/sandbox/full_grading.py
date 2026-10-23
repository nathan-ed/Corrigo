#!/usr/bin/env python3
"""SANDBOX ONLY, fictional copies only (see .claude/skills/corrigo-test): whole evaluations graded by lines in the app,
exercise by exercise over all the copies as a teacher does (lines typed, each placed by a click, Ctrl+Enter to the next
copy), then everything checked from the saved files: the text of each exercise exactly as typed, every grade against
an independent model of the rule (AGENTS.md), each placed text against its line (nothing stuck to another line, nothing
missing, nothing extra), the presentation marks and their grade; then every copy reopened (reopen_check.py).

    python3 scripts/sandbox/full_grading.py <tmp> --data <tmp>/xdg [--init-script …] [--offline] [--seed N] [--only A|B]
    python3 scripts/sandbox/full_grading.py <tmp> --data <tmp>/xdg … --replay <replay.json>   (replay_from_folder.py)

<tmp>/full/<evaluation> is (re)created. Screenshots of the failures go to <tmp>/full-shots."""
import argparse, glob, json, os, random, re, shutil, signal, subprocess, sys, time, traceback, unicodedata, yaml
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
W, H = A4
GRID_W, GRID_H = 165400, 233900
CHECK, CROSS = "$\\checkmark$", "$\\boldsymbol{\\times}$"

# Screen (1600×1000, default layout): panel on the left, the page of the exercise at the top of the view
GRADING_TAB = (80, 46)
SLOTS = [(850, 175 + 55 * k) for k in range(11)]  # Free column right of the answers, left of the grades

# Two fictional grade scales. An exercise: (name, total) or (name, [(sub-question, total)…])
EVALUATIONS = {
    "A": ("Test-complet-A", ["ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT", "GOLF", "HOTEL"], [
        ("PNF", 2.0),
        ("Q1", [("a)", 4.0), ("b)", 2.0)]),
        ("Q2", [("a)", 3.0), ("b)", 3.0)]),
        ("Q3", [("a", 2.0), ("b", 2.0), ("c", 2.0)]),
        ("Q4", [("Limites", 3.0), ("Mise en équation", 2.0), ("Calcul", 2.0)]),
        ("Q5", [("1. Limites", 3.0), ("2. Aire", 5.0)]),
    ]),
    "B": ("Test-complet-B", ["INDIA", "JULIETT", "KILO", "LIMA", "MIKE", "NOVEMBER"], [
        ("Ex 1", 4.0),
        ("Ex 2", [("a", 1.5), ("b", 1.5), ("c", 1.5), ("d", 1.5)]),
        ("Ex 3", 5.0),
        ("PNF", 2.0),
    ]),
    # A whole class: nothing gets slow with many copies (the lines of the other copies are read at each one)
    "C": ("Test-complet-C", ["ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT", "GOLF", "HOTEL", "INDIA", "JULIETT",
                             "KILO", "LIMA", "MIKE", "NOVEMBER", "OSCAR", "PAPA", "QUEBEC", "ROMEO", "SIERRA", "TANGO",
                             "UNIFORM", "VICTOR", "WHISKEY", "XRAY", "YANKEE", "ZULU"], [
        ("Ex 1", [("a", 2.0), ("b", 2.0)]),
        ("Ex 2", 3.0),
    ]),
}
TYPED = {}  # (code, exercise, index of the sub-question): a grade typed by hand (Shift+Tab), it stays whatever the lines


# LINES: what is typed, and what the rule says about it

class L:
    def __init__(self, typed, kind, points=None, shown=None, comment=None):
        self.typed, self.kind, self.points, self.comment = typed, kind, points, comment
        self.shown = typed if shown is None else shown
        self.written = kind != "PRIVATE"


# Each variant: the lines of a sub-question (kinds as in LineGrading; PRIVATE: in parentheses, never written)
VARIANTS = [
    [L("Correct", "FULL")],
    [L("Correct", "FULL"), L("Notation -0.5", "MISTAKE", -0.5, "Notation")],
    [L("Faux", "ZERO")],
    [L("Faux", "ZERO"), L("Calculs cohérents 1", "METHOD", 1, "Calculs cohérents")],
    [L("Erreur de signe -0.5", "MISTAKE", -0.5, "Erreur de signe"), L("Erreur de calcul -1", "MISTAKE", -1, "Erreur de calcul")],
    [L("Méthode juste +1", "METHOD", 1, "Méthode juste"), L("Bonne formule +0.5", "METHOD", 0.5, "Bonne formule")],
    [L("Pas de réponse", "ZERO")],
    [L("Correct", "FULL"), L("Notation -0.5 g", "MISTAKE", -0.5, "Notation")],
    [L("Correct", "FULL"), L("(vu au brouillon)", "PRIVATE"), L("Unités -0.5", "MISTAKE", -0.5, "Unités")],
    [L("Erreur -0.5", "MISTAKE", -0.5, "Erreur"), L("Erreur -0.5", "MISTAKE", -0.5, "Erreur")],
    [L("Incomplet", "ZERO"), L("Démarche +1", "METHOD", 1, "Démarche")],
    [L("Juste", "FULL"), L("PNF : unités", "PNF", comment="unités")],
    [L("Correct", "FULL"), L("Bien vu", "COMMENT")],
    [L("Erreur (-0,5)", "MISTAKE", -0.5, "Erreur")],
    [L("PNF", "PNF", comment=""), L("Cohérent", "FULL")],
    [L("Calcul juste (attention) +1", "METHOD", 1, "Calcul juste"), L("Oubli -0.5", "MISTAKE", -0.5, "Oubli")],
    [L("Faux (voir corrigé)", "ZERO", shown="Faux")],
    # Written as a teacher writes quickly: comma, "pt", capitals, a final dot
    [L("Correct", "FULL"), L("Unité oubliée -0,5 pt", "MISTAKE", -0.5, "Unité oubliée")],
    [L("EXACT", "FULL")],
    [L("Fausse.", "ZERO")],
    [L("Démarche 1,5 points", "METHOD", 1.5, "Démarche"), L("Calcul -0.5", "MISTAKE", -0.5, "Calcul")],
    [L("Méthode 0,5 pt", "METHOD", 0.5, "Méthode"), L("Erreur -1.", "MISTAKE", -1, "Erreur")],
]


def model_grade(lines, total):
    kinds = [line.kind for line in lines]
    pointed = [line.points for line in lines if line.points]
    if "FULL" in kinds: start = total
    elif "ZERO" in kinds: start = 0.0
    elif pointed: start = total if pointed[0] < 0 else 0.0
    else: return -1.0  # Not graded
    return min(total, max(0.0, start + sum(pointed)))


def fmt(x):
    return ("%g" % x)


def copy_text(line):
    if line.kind == "PNF": return "PNF" + (" : " + line.comment if line.comment else "")
    if line.kind == "FULL": return CHECK + (" " + line.shown if line.shown else "")
    if line.kind == "ZERO": return CROSS + (" " + line.shown if line.shown else "")
    if line.kind in ("METHOD", "MISTAKE"):
        points = ("+" if line.points > 0 else "−") + fmt(abs(line.points))
        text = line.shown + " (" + points + ")" if line.shown else points
        return CHECK + " " + text if line.kind == "METHOD" else text  # Points given: a green tick
    return line.shown


def label_of(name):
    # As LineGrading.labelOf: "a)" → "a)", "a" → "a)", "1. Limites" → "1.", "Calcul" → "Calcul)"
    if name.endswith(")") or name.endswith("."): return name
    if len(name) > 2 and name[0].isdigit() and ". " in name: return name.split(" ")[0]
    return name + ")"


# THE EVALUATION (PDF and edit files)

def gx(x): return int(x / W * GRID_W)
def gy(y): return int(y / H * GRID_H)


def make_evaluation(folder, copies, scale):
    os.makedirs(folder, exist_ok=True)
    for code_index, code in enumerate(copies):
        name = "%02d_%s" % (code_index + 1, code)
        c = canvas.Canvas(os.path.join(folder, name + ".pdf"), pagesize=A4)
        c.setFont("Helvetica-Bold", 20); c.drawCentredString(W / 2, H - 120, "Test fictif")
        c.setFont("Helvetica", 14); c.drawString(75, H - 190, "Copie : " + code)
        grades = [dict(x=gx(420), y=gy(560), page=0, index=0, parentPath="", value=-1.0,
                       total=sum(t if isinstance(t, float) else sum(s[1] for s in t) for _, t in scale), outOfTotal=-1.0,
                       name="Total", alwaysVisible=True)]
        page = 0
        for index, (exercise, content) in enumerate(scale):
            if exercise == "PNF":
                grades.append(dict(x=gx(370), y=gy(300 + 26 * index), page=0, index=index, parentPath="\\Total", value=-1.0,
                                   total=content, outOfTotal=-1.0, name=exercise, alwaysVisible=True))
                continue
            page += 1
            c.showPage()
            c.setFont("Helvetica-Bold", 13); c.drawString(50, H - 80, "Exercice " + exercise)
            c.setFont("Helvetica-Oblique", 13); c.setFillColorRGB(0.12, 0.25, 0.75)
            if isinstance(content, float):
                c.drawString(70, H - 150, "Réponse de " + code.lower())
                grades.append(dict(x=gx(470), y=gy(150), page=page, index=index, parentPath="\\Total", value=-1.0,
                                   total=content, outOfTotal=-1.0, name=exercise, alwaysVisible=True))
            else:
                grades.append(dict(x=gx(370), y=gy(300 + 26 * index), page=0, index=index, parentPath="\\Total", value=-1.0,
                                   total=sum(s[1] for s in content), outOfTotal=-1.0, name=exercise, alwaysVisible=True))
                for j, (sub, total) in enumerate(content):
                    c.drawString(70, H - (150 + 110 * j), sub + " réponse " + code.lower())
                    grades.append(dict(x=gx(470), y=gy(150 + 110 * j), page=page, index=j, parentPath="\\Total\\" + exercise,
                                       value=-1.0, total=total, outOfTotal=-1.0, name=sub, alwaysVisible=True))
            c.setFillColorRGB(0, 0, 0)
        c.showPage(); c.setFont("Helvetica", 12); c.drawString(50, H - 80, "Brouillon")  # The last exercise is not the last page
        c.save()
        edition = dict(versionID=14, lastScrollValue=0.0, skills={}, images={}, vectors={}, texts={}, grades=grades)
        with open(os.path.join(folder, "." + name + ".pdf.yml"), "w", encoding="utf-8") as f:
            yaml.safe_dump(edition, f, allow_unicode=True, sort_keys=False)
    return sorted(glob.glob(os.path.join(folder, "*.pdf")))


# THE APP

args = None
app = None
results = []


def xdo(*parts):
    return subprocess.run([sys.executable, os.path.join(HERE, "xdo.py")] + [str(p) for p in parts],
                          env=dict(os.environ, DISPLAY=args.display), capture_output=True, text=True).stdout


def click(point, wait=.6):
    xdo("click", *point)
    time.sleep(wait)


def key(combo, wait=.4):
    xdo("key", combo)
    time.sleep(wait)


def type_text(text, wait=.5):
    if text: xdo("type", text)
    time.sleep(wait)


def shot(name):
    os.makedirs(os.path.join(args.tmp, "full-shots"), exist_ok=True)
    subprocess.run(["import", "-window", "root", os.path.join(args.tmp, "full-shots", name + ".png")],
                   env=dict(os.environ, DISPLAY=args.display))


def open_copy():
    for line in xdo("titles").splitlines():
        if ".pdf" in line and "Corrigo" in line: return line.split(".pdf")[0].strip()
    return None


def check(name, condition, detail=""):
    results.append((name, bool(condition)))
    print(("  OK   " if condition else "  FAIL ") + name + ("" if condition else "  " + str(detail)[:600]), flush=True)
    if not condition: shot(name.replace(" ", "_").replace("/", "-")[:80])


def settings(folder, pdfs):
    settings_dir = os.path.join(args.data, "Corrigo")
    os.makedirs(settings_dir, exist_ok=True)
    for name, update in (("settings.yml", {"restoreLastSession": True, "storeEditionsNextToPdf": True, "checkUpdates": False,
                                           "sendStatistics": False, "gradingByLines": True, "linesAutoPlace": False}),
                         ("userdata.yml", None)):
        path = os.path.join(settings_dir, name)
        data = {}
        if os.path.exists(path):
            with open(path) as f: data = yaml.safe_load(f) or {}
        if update: data.update(update)
        else: data.setdefault("files", {})["lastFiles"] = pdfs
        with open(path, "w") as f: yaml.safe_dump(data, f, allow_unicode=True)


def start(pdf):
    global app
    env = dict(os.environ, DISPLAY=args.display, XDG_DATA_HOME=args.data, HOME=os.path.join(args.data, "home"))
    env.pop("SESSION_MANAGER", None)
    env.pop("DBUS_SESSION_BUS_ADDRESS", None)
    command = ["dbus-run-session", "--", os.path.join(REPO, "gradlew"), "--no-daemon", "-q"]
    if args.offline: command.append("--offline")
    if args.init_script: command += ["--init-script", args.init_script]
    command += ["run", "--args=" + pdf]
    log = os.path.join(args.tmp, "full-run.log")
    with open(log, "w") as out:
        app = subprocess.Popen(command, cwd=REPO, env=env, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(240):
        time.sleep(1)
        with open(log, errors="replace") as f:
            if "toOpenPath" in f.read(): break
    time.sleep(14)
    click(GRADING_TAB, 1.5)


def stop():
    global app
    key("ctrl+s", 2.5)
    os.killpg(app.pid, signal.SIGTERM)
    try:
        app.wait(20)
    except subprocess.TimeoutExpired:
        os.killpg(app.pid, signal.SIGKILL)
    app = None
    time.sleep(2)


def select_exercise(index):
    for _ in range(8): key("alt+Up", .3)
    for _ in range(index): key("alt+Down", .6)
    time.sleep(1)


def click_text_field():
    """Clicks the text of the lines (found on the screen: the tallest white box of the panel), under its last line."""
    path = os.path.join(args.tmp, "full-probe.png")
    subprocess.run(["import", "-window", "root", path], env=dict(os.environ, DISPLAY=args.display))
    from PIL import Image
    image = Image.open(path).convert("RGB")
    best, run_start = None, None
    for y in range(150, 720):
        white = image.getpixel((248, y)) == (255, 255, 255)  # The right edge of the field: no text there
        if white and run_start is None: run_start = y
        if (not white or y == 719) and run_start is not None:
            if best is None or y - run_start > best[1] - best[0]: best = (run_start, y)
            run_start = None
    if best is None or best[1] - best[0] < 60: raise RuntimeError("text field not found")
    click((248, best[1] - 8), .6)  # Under the last line: what is typed starts a new line


# GRADING

def plan(scale, copies, rng):
    """What is typed on each copy for each exercise: {(copy, exercise): (blocks, labels, during)}; blocks: [(leaf, lines)]."""
    todo = {}
    for code in copies:
        for exercise, content in scale:
            if exercise == "PNF": continue
            leaves = [(exercise, content)] if isinstance(content, float) else content
            blocks = [(j, rng.choice(VARIANTS)) for j in range(len(leaves))]
            labels = len(leaves) > 1 and rng.random() < .3
            if labels: blocks.reverse()  # The last sub-question first, each block with its label
            todo[(code, exercise)] = (blocks, labels, rng.random() < .5)
    return todo


def expected_text(blocks, labels, leaves):
    parts = []
    for leaf, lines in blocks:
        typed = [line.typed for line in lines]
        parts.append("\n".join(([label_of(leaves[leaf][0])] if labels else []) + typed))
    return "\n\n".join(parts)


def words(text):
    """As LineGrading.normalize, plus the final punctuation removed (the key of a line)."""
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if not unicodedata.combining(c)).lower()
    return re.sub(r"[\W_]+$", "", re.sub(r"\s+", " ", text).strip())


def only_one_starting(start):
    """A start of line that only one line of the variants has (Tab then takes it whatever was used before)."""
    keys = {words(line.shown) for variant in VARIANTS for line in variant if line.kind != "PNF"}
    return len([k for k in keys if k.startswith(words(start))]) == 1


def type_line(line, used, rng):
    """Types a line as a teacher does: a line already used on a copy graded before is often taken from the choices
    (its start, then Tab); sometimes Ctrl+S after it. Returns True if it was taken with Tab."""
    taken = line.kind in ("FULL", "ZERO", "COMMENT") and line.shown == line.typed and line.typed[-1].isalpha() \
        and line.typed in used and only_one_starting(line.typed[:-1]) and rng.random() < .6
    if taken:
        type_text(line.typed[:-1], .5)
        key("Tab", .5)
    else:
        type_text(line.typed, .5)
    if rng.random() < .15: key("ctrl+s", 1)
    return taken


def grade_exercise(blocks, labels, during, leaves, used, rng):
    """Types the lines of one exercise on the open copy (the text has the focus) and places each written line. After
    a line placed or taken with Tab, the next one is typed without Return: it must start a new line by itself."""
    slot = 0
    first = True
    for leaf, lines in blocks:
        if not first: type_text("\n\n", .3)
        if labels: type_text(label_of(leaves[leaf][0]) + "\n", .3)
        new_line_ready = False
        for j, line in enumerate(lines):
            if j > 0 and not new_line_ready: type_text("\n", .2)
            new_line_ready = type_line(line, used, rng)
            if during and line.written:
                click(SLOTS[slot], .9)
                slot += 1
                new_line_ready = True
        first = False
    if not during:
        for leaf, lines in blocks:
            for line in lines:
                if line.written:
                    click(SLOTS[slot], .8)
                    slot += 1
    for leaf, lines in blocks: used.update(line.typed for line in lines)


def run_evaluation(key_name):
    title, copies, scale = EVALUATIONS[key_name]
    TYPED.clear()
    folder = os.path.join(args.tmp, "full", title)
    shutil.rmtree(folder, ignore_errors=True)
    pdfs = make_evaluation(folder, copies, scale)
    names = [os.path.basename(p)[:-4] for p in pdfs]
    code_of = {n: n.split("_", 1)[1] for n in names}
    settings(folder, pdfs)
    rng = random.Random(args.seed + ord(key_name))
    todo = plan(scale, copies, rng)
    print("EVALUATION %s: %d copies, %d exercises" % (key_name, len(copies), len([e for e in scale if e[0] != "PNF"])), flush=True)
    start(pdfs[0])
    seen = {}
    waits = []  # Seconds from Ctrl+Enter to the next copy open
    used = {}  # The lines typed in each exercise on the copies graded before
    for index, (exercise, content) in enumerate(scale):
        if exercise == "PNF": continue
        leaves = [(exercise, content)] if isinstance(content, float) else content
        select_exercise(index)
        click_text_field()
        for n in range(len(copies)):
            name = open_copy()
            code = code_of.get(name)
            if code is None or (code, exercise) in seen:
                check("%s %s: copy %d opened by Ctrl+Enter" % (key_name, exercise, n + 1), False, (name, sorted(seen)))
                break
            blocks, labels, during = todo[(code, exercise)]
            grade_exercise(blocks, labels, during, leaves, used.setdefault(exercise, set()), rng)
            seen[(code, exercise)] = True
            before = time.time()
            key("ctrl+Return", .2)
            for _ in range(60):  # The next copy not graded for this exercise
                if open_copy() != name: break
                time.sleep(.25)
            if n + 1 < len(copies): waits.append(time.time() - before)
            time.sleep(4)
        check("%s %s: graded on every copy" % (key_name, exercise), all((code, exercise) in seen for code in copies), sorted(seen))
    if waits:
        check("%s: next copy open in %.1f s on average (at most %.1f s)" % (key_name, sum(waits) / len(waits), max(waits)),
              sum(waits) / len(waits) < 8 and max(waits) < 15, waits)
    revisit(key_name, scale, todo, names, code_of, rng)
    stop()
    verify(key_name, folder, scale, todo, names, code_of)
    reopen(key_name, folder)


def leaves_of(exercise, content):
    return [(exercise, content)] if isinstance(content, float) else content


def revisit(key_name, scale, todo, names, code_of, rng):
    """Back to copies graded before (Alt+PageUp/PageDown, Alt+↑ ↓): a line added at the end of an exercise and placed."""
    exercises = [(index, exercise, content) for index, (exercise, content) in enumerate(scale) if exercise != "PNF"]
    for _ in range(3):
        target = rng.choice(names)
        index, exercise, content = rng.choice(exercises)
        for _ in range(len(names)):
            current = open_copy()
            if current == target: break
            key("alt+Prior" if names.index(target) < names.index(current) else "alt+Next", 1)
            for _ in range(30):
                if open_copy() != current: break
                time.sleep(.5)
            time.sleep(3)
        check("%s: back to %s" % (key_name, code_of[target]), open_copy() == target, open_copy())
        select_exercise(index)  # From the text: the text of the exercise gets the focus, ready for a new line
        blocks, labels, during = todo[(code_of[target], exercise)]
        leaf, lines = blocks[-1]
        action = rng.choice(["add", "rewrite", "typed"])
        if action == "add":  # A line added at the end, placed by a click
            written = sum(1 for _, ls in blocks for line in ls if line.written)
            type_text("Bonus +0.5", .6)
            click(SLOTS[written], .9)
            blocks[-1] = (leaf, lines + [L("Bonus +0.5", "METHOD", 0.5, "Bonus")])
        elif action == "rewrite":  # The last line typed again: its text on the copy follows it, where it is
            key("ctrl+End", .3)
            key("shift+Home", .3)
            type_text("Erreur -1", .8)
            key("alt+Return", 1.5)  # A line that was a presentation mark or private is to place now
            blocks[-1] = (leaf, lines[:-1] + [L("Erreur -1", "MISTAKE", -1, "Erreur")])
        else:  # A grade typed by hand on the sub-question of the caret: it stays (✎)
            total = leaves_of(exercise, content)[leaf][1]
            value = rng.choice([0.0, total / 2, total])
            key("ctrl+End", .3)
            key("shift+Tab", .8)
            type_text(fmt(value), .3)
            key("Return", 1)
            TYPED[(code_of[target], exercise, leaf)] = value
        check("%s: back to %s, %s" % (key_name, code_of[target], action), True)
        key("ctrl+s", 2)


# REPLAY: texts graded before (replay_from_folder.py), typed again as they are

NATO = ["ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT", "GOLF", "HOTEL", "INDIA", "JULIETT", "KILO", "LIMA", "MIKE",
        "NOVEMBER", "OSCAR", "PAPA", "QUEBEC", "ROMEO", "SIERRA", "TANGO", "UNIFORM", "VICTOR", "WHISKEY", "XRAY", "YANKEE", "ZULU"]


def written_count(text, leaves):
    """The lines of a text written on the copy: not blank, not a label alone, not a private note alone."""
    labels = set()
    for name, _ in leaves:
        for label in (name, label_of(name), name.rstrip(").")):
            labels.add(words(label))
        if len(name) > 2 and name[0].isdigit() and ". " in name: labels.add(words(name.split(" ")[0]))
    count = 0
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped or (len(leaves) > 1 and words(stripped) in labels) or re.fullmatch(r"\([^()]*\)", stripped): continue
        count += 1
    return count


def run_replay(path):
    with open(path, encoding="utf-8") as f: data = json.load(f)
    scale = [(e[0], float(e[1]) if not isinstance(e[1], list) else [(s[0], float(s[1])) for s in e[1]]) for e in data["scale"]]
    copies = NATO[:len(data["copies"])]
    folder = os.path.join(args.tmp, "full", "Replay")
    shutil.rmtree(folder, ignore_errors=True)
    pdfs = make_evaluation(folder, copies, scale)
    names = [os.path.basename(p)[:-4] for p in pdfs]
    settings(folder, pdfs)
    rng = random.Random(args.seed)
    print("REPLAY: %d copies, %d exercises" % (len(copies), len([e for e in scale if e[0] != "PNF"])), flush=True)
    start(pdfs[0])
    for index, (exercise, content) in enumerate(scale):
        if exercise == "PNF": continue
        leaves = [(exercise, content)] if isinstance(content, float) else content
        select_exercise(index)
        click_text_field()
        for n, name in enumerate(names):
            if open_copy() != name:
                check("replay %s: copy %d open" % (exercise, n + 1), False, open_copy())
                break
            text = data["copies"][n]["texts"].get(exercise, "")
            if text:
                type_text(text, .8)
                # A few lines placed by a click, the others next to the grades (Alt+Enter)
                for slot in range(min(rng.randint(0, 3), written_count(text, leaves))): click(SLOTS[slot], .9)
                key("alt+Return", 1.5)
            if n + 1 < len(names):
                key("alt+Next", 1)
                for _ in range(30):
                    if open_copy() != name: break
                    time.sleep(.5)
                time.sleep(3)
        # Back to the first copy for the next exercise
        for _ in range(len(names)):
            if open_copy() == names[0]: break
            current = open_copy()
            key("alt+Prior", 1)
            for _ in range(30):
                if open_copy() != current: break
                time.sleep(.5)
            time.sleep(2)
    stop()
    verify_replay(folder, scale, data, names)
    reopen("replay", folder)


def verify_replay(folder, scale, data, names):
    for n, name in enumerate(names):
        base = load(folder, name)
        grades = {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in base.get("grades", [])}
        corrections = base.get("corrections") or {}
        linked = {t.get("correctionLine") for p in (base.get("texts") or {}).values() for t in p or [] if t.get("correctionLine")}
        problems = []
        for exercise, content in scale:
            text = data["copies"][n]["texts"].get(exercise, "")
            if exercise == "PNF" or not text: continue
            leaves = [(exercise, content)] if isinstance(content, float) else content
            saved = corrections.get("\\Total\\" + exercise) or {}
            if saved.get("text", "") != text: problems.append("%s: text %r ≠ typed %r" % (exercise, saved.get("text"), text))
            ids = [i for i in saved.get("ids", []) if i]
            placed = sum(1 for i in ids if i in linked)
            if placed != written_count(text, leaves): problems.append("%s: %d lines written on the copy, %d expected" % (exercise, placed, written_count(text, leaves)))
        for leaf, value in data["copies"][n]["expected"].items():
            if abs(grades.get(leaf, -9) - value) > 1e-9: problems.append("%s = %s, graded before %s" % (leaf, grades.get(leaf), value))
        check("replay copy %02d: texts, grades as before, lines written" % (n + 1), not problems, problems)


# CHECKS

class Loader(yaml.SafeLoader):
    pass
Loader.add_constructor("tag:yaml.org,2002:value", lambda loader, node: loader.construct_scalar(node))


def load(folder, name):
    with open(os.path.join(folder, "." + name + ".pdf.yml"), encoding="utf-8") as f: return yaml.load(f, Loader=Loader) or {}


def verify(key_name, folder, scale, todo, names, code_of):
    for name in names:
        code = code_of[name]
        base = load(folder, name)
        grades = {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in base.get("grades", [])}
        texts = [dict(t, page=page) for page, items in (base.get("texts") or {}).items() for t in items or []]
        corrections = base.get("corrections") or {}
        problems = []
        expected_marks, total, linked_seen = 0, 0.0, set()
        table_marks = {}
        for exercise, content in scale:
            path = "\\Total\\" + exercise
            if exercise == "PNF": continue
            leaves = [(exercise, content)] if isinstance(content, float) else content
            blocks, labels, during = todo[(code, exercise)]
            saved = corrections.get(path) or {}
            text = saved.get("text", "")
            if text.rstrip("\n") != expected_text(blocks, labels, leaves):
                problems.append("%s: text %r ≠ typed %r" % (exercise, text, expected_text(blocks, labels, leaves)))
            # Grades
            exercise_sum = 0.0
            for leaf, lines in blocks:
                leaf_path = path if isinstance(content, float) else path + "\\" + leaves[leaf][0]
                want = TYPED.get((code, exercise, leaf), model_grade(lines, leaves[leaf][1]))
                if abs(grades.get(leaf_path, -9) - want) > 1e-9:
                    problems.append("%s = %s, rule %s (%s)" % (leaf_path, grades.get(leaf_path), want, [l.typed for l in lines]))
                exercise_sum += max(want, 0)
            if not isinstance(content, float) and abs(grades.get(path, -9) - exercise_sum) > 1e-9:
                problems.append("%s = %s, sum %s" % (path, grades.get(path), exercise_sum))
            total += exercise_sum
            # Each written line on the copy, once, with its own text
            ids = saved.get("ids", [])
            raw_lines = text.split("\n")
            line_of = {}
            k = 0
            for leaf, lines in blocks:
                if labels: k += 1
                for line in lines:
                    if k < len(ids) and ids[k]: line_of[ids[k]] = line
                    k += 1
                k += 1  # Blank line
            if len(raw_lines) != len(ids): problems.append("%s: %d lines, %d ids" % (exercise, len(raw_lines), len(ids)))
            for line_id, line in line_of.items():
                placed = [t for t in texts if t.get("correctionLine") == line_id]
                if not line.written:
                    if placed: problems.append("%s: private line written %r" % (exercise, placed[0]["text"]))
                    continue
                if len(placed) != 1:
                    problems.append("%s: line %r placed %d times" % (exercise, line.typed, len(placed)))
                    continue
                if placed[0]["text"] != copy_text(line):
                    problems.append("%s: line %r written %r, expected %r" % (exercise, line.typed, placed[0]["text"], copy_text(line)))
                if line.kind == "PNF":
                    expected_marks += 1
                    table_marks[exercise] = table_marks.get(exercise, 0) + 1
                linked_seen.add(line_id)
        # Nothing else on the copy than the placed lines and the table of the presentation marks (first page)
        for t in texts:
            if t.get("correctionLine") in linked_seen: continue
            if t.get("correctionLine") or t["page"] != "page0":
                problems.append("text not expected %r on %s" % (t.get("text"), t["page"]))
        rows = [t for t in texts if t["page"] == "page0" and not t.get("correctionLine") and str(t.get("text", ""))[:1].isdigit()]
        bars = sum(str(t.get("text", "")).split(".", 1)[-1].count("I") for t in rows)
        if bars != sum(min(4, n) for n in table_marks.values()):
            problems.append("table: %d bar(s), marks by exercise %s" % (bars, table_marks))
        marks = sum(1 for t in texts if t.get("correctionLine") and str(t.get("text", "")).startswith("PNF"))
        if marks != expected_marks: problems.append("presentation marks %d, lines %d" % (marks, expected_marks))
        pnf = [e for e in scale if e[0] == "PNF"]
        if pnf:
            want = min(pnf[0][1], max(0.0, 2 - .5 * max(0, expected_marks - 1)))
            if abs(grades.get("\\Total\\PNF", -9) - want) > 1e-9: problems.append("PNF = %s, %d mark(s): %s" % (grades.get("\\Total\\PNF"), expected_marks, want))
            total += want
        if abs(grades.get("\\Total", -9) - total) > 1e-9: problems.append("Total = %s, expected %s" % (grades.get("\\Total"), total))
        check("%s %s: text, grades, written lines, marks" % (key_name, code), not problems, problems)


def reopen(key_name, folder):
    command = [sys.executable, os.path.join(HERE, "reopen_check.py"), folder, "--data", args.data, "--rounds", "2"]
    if args.offline: command.append("--offline")
    if args.init_script: command += ["--init-script", args.init_script]
    out = subprocess.run(command, capture_output=True, text=True)
    check("%s: every copy reopened twice, nothing changed" % key_name, out.returncode == 0 and "All the copies are unchanged" in out.stdout,
          out.stdout[-1500:] + out.stderr[-500:])


def main():
    global args
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tmp")
    parser.add_argument("--data", required=True)
    parser.add_argument("--init-script", default=None)
    parser.add_argument("--display", default=":77")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--only", default=None)
    parser.add_argument("--replay", default=None, help="JSON of replay_from_folder.py: these texts typed again, not A and B")
    args = parser.parse_args()
    if subprocess.run(["pgrep", "-f", "Xvfb " + args.display], stdout=subprocess.DEVNULL).returncode != 0:
        subprocess.Popen(["Xvfb", args.display, "-screen", "0", "1600x1000x24"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)
    for name in [] if args.replay else EVALUATIONS:
        if args.only and args.only != name: continue
        try:
            run_evaluation(name)
        except Exception:  # noqa: BLE001
            check("evaluation %s ran" % name, False, traceback.format_exc(limit=4))
            if app is not None: stop()
    if args.replay:
        try:
            run_replay(args.replay)
        except Exception:  # noqa: BLE001
            check("replay ran", False, traceback.format_exc(limit=4))
            if app is not None: stop()
    failed = [r for r in results if not r[1]]
    print("\n%d checks, %d failed" % (len(results), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
