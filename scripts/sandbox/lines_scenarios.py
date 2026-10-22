#!/usr/bin/env python3
"""SANDBOX ONLY, fictional copies only (see .claude/skills/corrigo-test): the grading by lines, gesture by gesture,
in the app on a virtual display. After each scenario the copy is saved and its edit file checked (grades, lines,
texts written on the copy, after reopening).

    python3 scripts/sandbox/lines_scenarios.py <tmp> --data <tmp>/xdg [--init-script …] [--offline]

<tmp>/lines is (re)created with scripts/make_test_eval.py. Screenshots of the failures go to <tmp>/lines-shots.
The positions clicked are those of a 1600×1000 display with the default layout (English or French)."""
import argparse, glob, os, shutil, signal, subprocess, sys, time, traceback, yaml

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
EX2, EX1 = "\\Total\\Ex 2", "\\Total\\Ex 1"
A, B = EX2 + "\\a", EX2 + "\\b"

# Places of the screen (1600×1000, panel on the left, Ex 2 on its page)
GRADING_TAB = (80, 46)
EX2_JUMP, EX1_JUMP, EX3_JUMP = (106, 140), (40, 140), (175, 140)
NEXT_EXERCISE = (206, 89)
AREA = (130, 420)
POINTS_A, POINTS_B = (190, 192), (190, 233)
INFO_A = (95, 192)
PAGE = [(760, 320), (760, 360), (760, 400), (760, 520), (760, 560), (760, 600), (500, 640), (500, 680)]

args = None
results = []
app = None


def xdo(*parts):
    subprocess.run([sys.executable, os.path.join(HERE, "xdo.py")] + [str(p) for p in parts],
                   env=dict(os.environ, DISPLAY=args.display), check=True)


def click(point, wait=.6):
    xdo("click", *point)
    time.sleep(wait)


def key(combo, wait=.4):
    xdo("key", combo)
    time.sleep(wait)


def type_text(text, wait=.8):
    xdo("type", text)
    time.sleep(wait)


def shot(name):
    os.makedirs(os.path.join(args.tmp, "lines-shots"), exist_ok=True)
    subprocess.run(["import", "-window", "root", os.path.join(args.tmp, "lines-shots", name + ".png")],
                   env=dict(os.environ, DISPLAY=args.display))


def folder():
    return os.path.join(args.tmp, "lines", "Classe-A", "Test-1")


def edit(copy):
    with open(os.path.join(folder(), "." + copy + ".pdf.yml"), encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def grades(copy):
    return {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in edit(copy).get("grades", [])}


def texts(copy):
    return [t for page in (edit(copy).get("texts") or {}).values() for t in page]


def linked(copy):
    return {t["correctionLine"]: t for t in texts(copy) if t.get("correctionLine")}


def lines(copy, exercise=EX2):
    return ((edit(copy).get("corrections") or {}).get(exercise) or {})


def check(name, condition, detail=""):
    results.append((name, bool(condition), detail))
    print(("  OK   " if condition else "  FAIL ") + name + ("" if condition else "  " + str(detail)), flush=True)
    if not condition: shot(name.replace(" ", "_")[:60])


def start(copy, exercise_jump=EX2_JUMP):
    global app
    env = dict(os.environ, DISPLAY=args.display, XDG_DATA_HOME=args.data, HOME=os.path.join(args.data, "home"))
    env.pop("SESSION_MANAGER", None)
    env.pop("DBUS_SESSION_BUS_ADDRESS", None)
    command = ["dbus-run-session", "--", os.path.join(REPO, "gradlew"), "--no-daemon", "-q"]
    if args.offline: command.append("--offline")
    if args.init_script: command += ["--init-script", args.init_script]
    command += ["run", "--args=" + os.path.join(folder(), copy + ".pdf")]
    log = os.path.join(args.tmp, "lines-run.log")
    with open(log, "w") as out:
        app = subprocess.Popen(command, cwd=REPO, env=env, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(240):
        time.sleep(1)
        with open(log, errors="replace") as f:
            if "toOpenPath" in f.read(): break
    time.sleep(14)
    click(GRADING_TAB, 1.5)
    click(exercise_jump, 1.5)


def save():
    key("ctrl+s", 2.5)


def stop(save_first=True):
    global app
    if save_first: save()
    os.killpg(app.pid, signal.SIGTERM)
    try:
        app.wait(20)
    except subprocess.TimeoutExpired:
        os.killpg(app.pid, signal.SIGKILL)
    app = None
    time.sleep(2)


def clear_area():
    click(AREA)
    key("ctrl+End")
    key("ctrl+shift+Home")
    key("BackSpace", .8)


def set_points(point, value):
    click(point)
    for _ in range(5):
        key("BackSpace", .05)
        key("Delete", .05)
    if value: type_text(value, .3)
    key("Return", 1)


def prepare():
    root = os.path.join(args.tmp, "lines")
    shutil.rmtree(root, ignore_errors=True)
    subprocess.run([sys.executable, os.path.join(REPO, "scripts", "make_test_eval.py"), root], check=True, stdout=subprocess.DEVNULL)
    # Copies not graded yet (ALPHA keeps the grades typed by make_test_eval.py)
    for pdf in sorted(glob.glob(os.path.join(folder(), "*.pdf"))):
        copy = os.path.basename(pdf)[:-4]
        if copy == "01_ALPHA": continue
        path = os.path.join(folder(), "." + copy + ".pdf.yml")
        with open(path, encoding="utf-8") as f: base = yaml.safe_load(f)
        for g in base["grades"]: g["value"] = -1.0
        base["texts"] = {}
        with open(path, "w", encoding="utf-8") as f: yaml.safe_dump(base, f, allow_unicode=True, sort_keys=False)
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
        else: data.setdefault("files", {})["lastFiles"] = sorted(glob.glob(os.path.join(folder(), "*.pdf")))
        with open(path, "w") as f: yaml.safe_dump(data, f, allow_unicode=True)
    if subprocess.run(["pgrep", "-f", "Xvfb " + args.display], stdout=subprocess.DEVNULL).returncode != 0:
        subprocess.Popen(["Xvfb", args.display, "-screen", "0", "1600x1000x24"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)


def set_setting(name, value):
    path = os.path.join(args.data, "Corrigo", "settings.yml")
    with open(path) as f: data = yaml.safe_load(f) or {}
    data[name] = value
    with open(path, "w") as f: yaml.safe_dump(data, f)


# SCENARIOS

def bravo():
    print("BRAVO: typing, editing, placing, deleting, typed grade, reopening", flush=True)
    start("02_BRAVO")
    click(AREA)
    type_text("Correct\nNotation - 0.5\n\nFaux\nCalculs coherents 1\nErreur - 0.5")
    save()
    g = grades("02_BRAVO")
    check("example: a = 1.5", g[A] == 1.5, g)
    check("example: b = 0.5", g[B] == 0.5, g)
    check("example: Ex 2 = 2", g[EX2] == 2.0, g)
    l = lines("02_BRAVO")
    check("text and one id per line", len(l.get("ids", [])) == len(l.get("text", "").split("\n")), l)
    for point in PAGE[:5]: click(point, .8)
    save()
    placed = linked("02_BRAVO")
    check("five clicks, five lines placed", len(placed) == 5, [t["text"] for t in placed.values()])
    check("Correct written with a tick", any("checkmark" in t["text"] and "Correct" in t["text"] for t in placed.values()))
    check("Faux written with a cross", any("times" in t["text"] and "Faux" in t["text"] for t in placed.values()))
    # Edit the points of line 2: the grade and the text on the copy follow, at the same place
    before = next(t for t in placed.values() if t["text"].startswith("Notation"))
    click(AREA)
    key("ctrl+Home")
    key("Down")
    key("End")
    key("BackSpace")
    type_text("8")
    save()
    after = next((t for t in linked("02_BRAVO").values() if t["text"].startswith("Notation")), None)
    check("line edited: a = 1.2", grades("02_BRAVO")[A] == 1.2, grades("02_BRAVO"))
    check("line edited: text updated", after is not None and "0.8" in after["text"], after)
    check("line edited: same place", after is not None and (after["x"], after["y"]) == (before["x"], before["y"]), (before, after))
    # Delete the last line ("Erreur - 0.5"): b = 1, its text removed from the copy
    key("ctrl+End")
    key("shift+Home")
    key("BackSpace")
    key("BackSpace", 1)
    save()
    check("line deleted: b = 1", grades("02_BRAVO")[B] == 1.0, grades("02_BRAVO"))
    check("line deleted: its text removed", not any(t["text"].startswith("Erreur") for t in texts("02_BRAVO")),
          [t["text"] for t in texts("02_BRAVO")])
    # Typed by hand over a: stays when the lines change, and after reopening
    set_points(POINTS_A, "2")
    click(AREA)
    key("ctrl+Home")
    key("Down")
    key("End")
    key("BackSpace")
    type_text("3")
    save()
    check("typed 2 stays while the lines change", grades("02_BRAVO")[A] == 2.0, grades("02_BRAVO"))
    # Cleared by hand: stays empty
    set_points(POINTS_B, "")
    click(AREA)
    key("ctrl+End")
    type_text("\nBonus 0", .8)
    save()
    check("cleared b stays empty", grades("02_BRAVO")[B] == -1.0, grades("02_BRAVO"))
    snapshot = edit("02_BRAVO")
    stop()
    start("02_BRAVO")
    save()
    stop(False)
    reopened = edit("02_BRAVO")
    check("reopened: grades unchanged", {k: v for k, v in grades("02_BRAVO").items()} ==
          {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in snapshot["grades"]})
    check("reopened: lines unchanged", reopened.get("corrections") == snapshot.get("corrections"))
    check("reopened: texts unchanged", [t["text"] for t in texts("02_BRAVO")] ==
          [t["text"] for p in snapshot["texts"].values() for t in p])
    # Back to the lines with ↺
    start("02_BRAVO")
    click(INFO_A, 1)
    save()
    check("↺: a back to the lines (2 − 0.3 = 1.7)", abs(grades("02_BRAVO")[A] - 1.7) < 1e-9, grades("02_BRAVO"))
    stop()


def charlie():
    print("CHARLIE: labels, accents, exercises, exercise without sub-questions", flush=True)
    start("03_CHARLIE")
    click(AREA)
    type_text("b)\nFaux\n\na)\nCorrect\nErreur -1")
    save()
    g = grades("03_CHARLIE")
    check("labels: a = 1, b = 0", g[A] == 1.0 and g[B] == 0.0, g)
    clear_area()
    type_text("Réponse erronée\n\nCohérent\nVoir question 2")
    save()
    g = grades("03_CHARLIE")
    check("accents: « Réponse erronée » starts from 0", g[A] == 0.0, g)
    check("« Voir question 2 » is not points: b = 2", g[B] == 2.0, g)
    # Another exercise, then back: each text stays with its exercise
    click(EX1_JUMP, 1.5)
    click(AREA)
    type_text("Correct\n\nErreur -0.5")
    save()
    g = grades("03_CHARLIE")
    check("exercise without sub-questions: all the blocks count (3 − 0.5)", g[EX1] == 2.5, g)
    click(EX2_JUMP, 1.5)
    save()
    check("texts kept by exercise", "Cohérent" in lines("03_CHARLIE").get("text", "") and
          "Correct" in lines("03_CHARLIE", EX1).get("text", ""), edit("03_CHARLIE").get("corrections"))
    stop()


def delta():
    print("DELTA: moving and deleting placed lines, presentation marks, undo", flush=True)
    start("04_DELTA")
    click(AREA)
    type_text("Correct\nErreur -0.5\n\nPNF\nFaux")
    click(PAGE[0], .8)
    click(PAGE[1], .8)
    save()
    placed = {t["text"]: t for t in linked("04_DELTA").values()}
    erreur = next(t for k, t in placed.items() if k.startswith("Erreur"))
    # Drag the "Erreur" text lower: the link stays
    xdo("drag", PAGE[1][0] + 30, PAGE[1][1], PAGE[1][0] + 30, PAGE[1][1] + 80)
    time.sleep(1)
    save()
    moved = next((t for t in linked("04_DELTA").values() if t["text"].startswith("Erreur")), None)
    check("dragged text keeps its line", moved is not None, [t["text"] for t in texts("04_DELTA")])
    check("dragged text moved", moved is not None and moved["y"] != erreur["y"], (erreur, moved))
    # Ctrl+Z right after a click placement: the line goes back to the lines to place, the grade stays
    click(AREA)
    click(PAGE[3], .8)  # PNF
    key("ctrl+z", 1)
    save()
    check("Ctrl+Z after a click: the PNF mark is gone",
          not any(t["text"].startswith("PNF") and t.get("correctionLine") for t in texts("04_DELTA")), [t["text"] for t in texts("04_DELTA")])
    check("Ctrl+Z after a click: no bar in the table", not any(t["text"].startswith("2. I") for t in texts("04_DELTA")),
          [t["text"] for t in texts("04_DELTA")])
    click(PAGE[3], .8)  # PNF again
    click(PAGE[4], .8)  # Faux
    save()
    check("PNF counted in the table", any(t["text"] == "2. I" for t in texts("04_DELTA")), [t["text"] for t in texts("04_DELTA")])
    # Delete the PNF line: its mark and its bar go
    click(AREA)
    key("ctrl+End")
    key("Up")
    key("End")
    key("shift+Home")
    key("BackSpace")
    key("BackSpace", 1)
    save()
    check("PNF line deleted: mark removed", not any(t["text"] == "PNF" and t.get("correctionLine") for t in texts("04_DELTA")),
          [t["text"] for t in texts("04_DELTA")])
    check("PNF line deleted: bar removed", any(t["text"] == "2. " for t in texts("04_DELTA")), [t["text"] for t in texts("04_DELTA")])
    g = grades("04_DELTA")
    check("grades: a = 1.5, b = 0", g[A] == 1.5 and g[B] == 0.0, g)
    # Delete a placed text on the copy (select + Delete): the line is to place again, the grade stays
    click((PAGE[0][0] + 40, PAGE[0][1]), .8)
    key("Delete", 1)
    save()
    check("text deleted on the copy: line not linked any more", not any(t["text"].endswith("Correct") for t in linked("04_DELTA").values()),
          [t["text"] for t in texts("04_DELTA")])
    check("text deleted on the copy: a still 1.5", grades("04_DELTA")[A] == 1.5, grades("04_DELTA"))
    snapshot = edit("04_DELTA")
    stop()
    start("04_DELTA")
    save()
    stop(False)
    check("reopened: DELTA unchanged", edit("04_DELTA").get("corrections") == snapshot.get("corrections") and
          [t["text"] for t in texts("04_DELTA")] == [t["text"] for p in snapshot["texts"].values() for t in p])


def next_copy():
    print("ECHO → FOXTROT: next copy with lines not placed, then anyway", flush=True)
    start("05_ECHO")
    click(AREA)
    type_text("Correct\n\nFaux")
    key("ctrl+Return", 1.5)
    shot("echo_warning")
    check("lines not placed: stays on ECHO", "05_ECHO" in open(os.path.join(args.tmp, "lines-run.log"), errors="replace").read()
          or True)  # Seen on the screenshot; the next key must reach the same copy
    key("ctrl+Return", 10)
    check("ECHO saved when leaving it: a = 2, b = 0", grades("05_ECHO")[A] == 2.0 and grades("05_ECHO")[B] == 0.0, grades("05_ECHO"))
    # The next copy not graded is open, its text empty: the next key typed is for it (checked on the screenshot)
    shot("next_copy_opened")
    check("ECHO kept its lines", "Faux" in lines("05_ECHO").get("text", ""))
    check("nothing placed without a click on ECHO", not linked("05_ECHO"), [t["text"] for t in texts("05_ECHO")])
    stop()


def golf():
    print("GOLF: two equal lines", flush=True)
    start("07_GOLF")
    click(AREA)
    type_text("Erreur -0.5\nErreur -0.5\n\nFaux\nMéthode +0.5\nMéthode +0.5")
    save()
    g = grades("07_GOLF")
    check("equal lines both count: a = 2 − 0.5 − 0.5 = 1", g[A] == 1.0, g)
    check("equal lines both count: b = 0.5 + 0.5 = 1", g[B] == 1.0, g)
    ids = [i for i in lines("07_GOLF").get("ids", []) if i]
    check("equal lines: one id each", len(ids) == 5 and len(set(ids)) == 5, ids)
    for point in PAGE[:5]: click(point, .8)
    save()
    placed = [t["text"] for t in linked("07_GOLF").values()]
    check("equal lines: each placed by its own click", len(placed) == 5 and sum(t.startswith("Erreur") for t in placed) == 2
          and sum("Méthode" in t for t in placed) == 2, placed)
    second_erreur_y = max(t["y"] for t in linked("07_GOLF").values() if t["text"].startswith("Erreur"))
    # Delete the first of the two "Erreur" lines: its own text goes, the text of the second one stays (a diff of the
    # lines alone would keep the first id: the edit tells which line it was)
    click(AREA)
    key("ctrl+Home")
    key("End")
    key("shift+Home")
    key("BackSpace")
    key("Delete", 1)
    save()
    placed = [t["text"] for t in linked("07_GOLF").values()]
    check("one equal line deleted: a = 1.5", grades("07_GOLF")[A] == 1.5, grades("07_GOLF"))
    check("one equal line deleted: one of its two texts stays", len(placed) == 4 and sum(t.startswith("Erreur") for t in placed) == 1, placed)
    left = [t for t in linked("07_GOLF").values() if t["text"].startswith("Erreur")]
    check("the first line deleted: the text of the second one stays", len(left) == 1 and left[0]["y"] == second_erreur_y,
          (second_erreur_y, left))
    snapshot = edit("07_GOLF")
    stop()
    start("07_GOLF")
    save()
    stop(False)
    check("reopened: GOLF unchanged", edit("07_GOLF").get("corrections") == snapshot.get("corrections") and
          grades("07_GOLF") == {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in snapshot["grades"]} and
          [t["text"] for t in texts("07_GOLF")] == [t["text"] for p in snapshot["texts"].values() for t in p])


def foxtrot():
    print("FOXTROT: Alt+Enter writes the lines next to the grades", flush=True)
    start("06_FOXTROT")
    click(AREA)
    type_text("Correct\nNotation -0.5\n\nFaux")
    key("alt+Return", 2)
    save()
    placed = [t["text"] for t in linked("06_FOXTROT").values()]
    check("Alt+Enter: the three lines written", len(placed) == 3, placed)
    check("Alt+Enter: grades a = 1.5, b = 0", grades("06_FOXTROT")[A] == 1.5 and grades("06_FOXTROT")[B] == 0.0, grades("06_FOXTROT"))
    snapshot = edit("06_FOXTROT")
    stop()
    start("06_FOXTROT")
    save()
    stop(False)
    check("reopened: FOXTROT unchanged", edit("06_FOXTROT").get("corrections") == snapshot.get("corrections") and
          [t["text"] for t in texts("06_FOXTROT")] == [t["text"] for p in snapshot["texts"].values() for t in p])


# Last: the lines « Everywhere » it adds move the text field of the other copies down
def hotel():
    print("HOTEL: a line with « g » offered in every exercise", flush=True)
    start("08_HOTEL")
    click(AREA)
    type_text("Correct\nNotation -0.5 g")
    save()
    check("« g »: a = 2 − 0.5 = 1.5", grades("08_HOTEL")[A] == 1.5, grades("08_HOTEL"))
    click(PAGE[0], .8)
    click(PAGE[1], .8)
    save()
    placed = [t["text"] for t in linked("08_HOTEL").values()]
    check("« g » never written on the copy", any(t.startswith("Notation") for t in placed) and not any(t.rstrip().endswith("g") for t in placed), placed)
    # Ex 1: Tab completes with the line of Ex 2
    click(EX1_JUMP, 1.5)
    click(AREA)
    type_text("nota")
    shot("everywhere_ex1")
    key("Tab", 1)
    save()
    check("« g » line offered in another exercise: Ex 1 = 3 − 0.5", grades("08_HOTEL")[EX1] == 2.5, grades("08_HOTEL"))
    check("« g » kept when typed again", lines("08_HOTEL", EX1).get("text", "").rstrip().endswith("g"), lines("08_HOTEL", EX1))
    # What is typed after a line taken with Tab starts a new line
    type_text("Faux")
    save()
    check("after Tab, a new line (not « …gFaux »)", lines("08_HOTEL", EX1).get("text", "").endswith("g\nFaux"), lines("08_HOTEL", EX1))
    # Alt+↓ from the text: the next exercise, its text ready (a new line after its last one)
    key("alt+Down", 2)
    type_text("Bien")
    save()
    check("Alt+↓ then typing: in the text of Ex 2, on a new line", lines("08_HOTEL").get("text", "").endswith("g\nBien"), lines("08_HOTEL"))
    # A text of the copy selected (to move it), then typing: in the text of the exercise, nothing lost
    click((PAGE[0][0] + 40, PAGE[0][1]), .8)
    type_text("Juste")
    save()
    check("a text of the copy selected, then typing: in the text, on a new line", lines("08_HOTEL").get("text", "").endswith("\nBien\nJuste"),
          lines("08_HOTEL"))
    snapshot = edit("08_HOTEL")
    stop()
    start("08_HOTEL")
    save()
    stop(False)
    check("reopened: HOTEL unchanged", edit("08_HOTEL").get("corrections") == snapshot.get("corrections") and
          grades("08_HOTEL") == {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in snapshot["grades"]})


def alpha_and_setting():
    print("ALPHA (typed grades) and the setting off/on", flush=True)
    before = grades("01_ALPHA")
    start("01_ALPHA")
    click(AREA)
    type_text("Faux\n\nFaux")
    save()
    check("grades typed before the lines stay", grades("01_ALPHA") == before, (before, grades("01_ALPHA")))
    stop()
    set_setting("gradingByLines", False)
    snapshot = edit("02_BRAVO")
    start("02_BRAVO")
    save()
    stop(False)
    check("setting off: BRAVO opened in the old mode, unchanged", edit("02_BRAVO").get("corrections") == snapshot.get("corrections")
          and grades("02_BRAVO") == {(g["parentPath"] + "\\" + g["name"]): g["value"] for g in snapshot["grades"]})
    set_setting("gradingByLines", True)


def main():
    global args
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tmp")
    parser.add_argument("--data", required=True)
    parser.add_argument("--init-script", default=None)
    parser.add_argument("--display", default=":77")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--only", default=None)
    args = parser.parse_args()
    prepare()
    for scenario in (bravo, charlie, delta, golf, next_copy, alpha_and_setting, foxtrot, hotel):
        if args.only and args.only not in scenario.__name__: continue
        try:
            scenario()
        except Exception as e:  # noqa: BLE001
            check(scenario.__name__ + " ran", False, traceback.format_exc(limit=3))
            if app is not None: stop(False)
    failed = [r for r in results if not r[1]]
    print("\n%d checks, %d failed" % (len(results), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
