#!/usr/bin/env python3
"""Fictional copies for the reopening check (scripts/sandbox/reopen_check.py): one copy per kind of grading that must
not change when the copy is opened again. Builds the fictional evaluation of make_test_eval.py, then rewrites the
edit files of Classe-A/Test-1. No real student, name or data.

    python3 scripts/make_reopen_cases.py <dir>

01_ALPHA   grades typed by hand (as made by make_test_eval.py)
02_BRAVO   grading by lines: lines placed, a line not placed, a presentation mark counted in the table
03_CHARLIE grading by lines with a grade typed by hand over the lines
04_DELTA   presentation exercise (PNF) typed by hand, no mark
05_ECHO    1.7 methods and mistakes (tags): a grade typed by hand over a mistake
06_FOXTROT 1.7 methods and mistakes: a grade computed from a typed value and a mistake
07_GOLF    1.6 scored comment: a method from 0, no mode kept
08_HOTEL   grading by lines on an exercise without sub-questions, two blocks
"""
import os, subprocess, sys, yaml

ROOT = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))
subprocess.run([sys.executable, os.path.join(HERE, "make_test_eval.py"), ROOT], check=True, stdout=subprocess.DEVNULL)
D = os.path.join(ROOT, "Classe-A", "Test-1")
TOTAL, EX2, EX1 = "\\Total", "\\Total\\Ex 2", "\\Total\\Ex 1"


def load(name):
    with open(os.path.join(D, "." + name + ".pdf.yml"), encoding="utf-8") as f:
        return yaml.safe_load(f)


def save(name, base):
    sums(base)
    with open(os.path.join(D, "." + name + ".pdf.yml"), "w", encoding="utf-8") as f:
        yaml.safe_dump(base, f, allow_unicode=True, sort_keys=False)


def grade(base, parent, name):
    return next(g for g in base["grades"] if g["parentPath"] == parent and g["name"] == name)


def sums(base):
    for parent in sorted({g["parentPath"] for g in base["grades"]}, key=lambda p: -p.count("\\")):
        path = parent
        parent_grade = next((g for g in base["grades"] if (g["parentPath"] + "\\" + g["name"]) == path), None)
        if parent_grade is None:
            continue
        children = [g for g in base["grades"] if g["parentPath"] == path]
        values = [g["value"] for g in children if g["value"] >= 0]
        parent_grade["value"] = sum(values) if values else -1.0
        parent_grade["total"] = sum(g["total"] for g in children)


def text(page_x, page_y, content, color="0x1565c0ff", size=14.0, **extra):
    data = {"x": page_x, "y": page_y, "color": color, "font": "Open Sans", "size": size, "bold": False, "italic": False,
            "text": content, "maxWidth": 30.0}
    data.update(extra)
    return data


def reset(base):
    for g in base["grades"]:
        g["value"] = -1.0
        for key in ("valueSource", "valueBeforeComments", "pointsStart", "pointsMode", "pointsAutoMode"):
            g.pop(key, None)
    base["texts"] = {}
    base.pop("corrections", None)


def table(base, row_marks):
    """PNF table of the first page: header and one row per exercise (Ex 1, Ex 2, Ex 3)"""
    rows = [text(3199, 130459, "PNF", "0xb31a1aff", 18.0)]
    for i, marks in enumerate(row_marks):
        label = "%d. " % (i + 1)
        rows.append(text(3199, 130459 + 8186 * (i + 1), label + "  ".join(["I"] * marks) if marks else label, "0xb31a1aff", 18.0))
    base["texts"].setdefault("page0", []).extend(rows)


# 02_BRAVO: grading by lines
b = load("02_BRAVO"); reset(b)
lines = "Correct\nNotation - 0.5\n\nFaux\nCalculs coherents 1\nPNF\nErreur - 0.5"
ids = ["l1", "l2", "", "l3", "l4", "l5", "l6"]
grade(b, EX2, "a")["value"] = 1.5
grade(b, EX2, "b")["value"] = 0.5
b["corrections"] = {EX2: {"text": lines, "ids": ids, "computed": {EX2 + "\\a": 1.5, EX2 + "\\b": 0.5}}}
b["texts"]["page2"] = [
    text(78488, 45841, "$\\checkmark$ Correct", "0x2e7d32ff", correctionLine="l1"),
    text(77704, 51058, "Notation (−0.5)", "0xc62828ff", correctionLine="l2"),
    text(77704, 85466, "Faux", "0xc62828ff", correctionLine="l3"),
    text(77704, 73257, "PNF", "0xb31a1aff", 24.0, correctionLine="l5", pnfExercise="Ex 2"),
    # l4 and l6 are not placed yet
]
table(b, [0, 1, 0])
save("02_BRAVO", b)

# 03_CHARLIE: grading by lines, a typed by hand (2) over the lines (1.5)
c = load("03_CHARLIE"); reset(c)
grade(c, EX2, "a")["value"] = 2.0
grade(c, EX2, "b")["value"] = 0.0
c["corrections"] = {EX2: {"text": "Correct\nNotation -0.5\n\nFaux", "ids": ["m1", "m2", "", "m3"],
                          "computed": {EX2 + "\\a": 1.5, EX2 + "\\b": 0.0}}}
save("03_CHARLIE", c)

# 04_DELTA: presentation exercise typed by hand, no mark
d = load("04_DELTA")
d["grades"].append({"x": 120000, "y": 30000, "page": 0, "index": 3, "parentPath": TOTAL, "value": 1.0, "total": 2.0,
                    "outOfTotal": -1.0, "name": "PNF", "alwaysVisible": True})
save("04_DELTA", d)


# 05_ECHO and 06_FOXTROT: 1.7 methods and mistakes
def tag_element(use_id, path, points, comment="Erreur formule"):
    return text(40000, 60000, "%s (−%g)" % (comment, abs(points)), "0xc62828ff", 18.0, scoredCommentId="tag:T1#" + use_id,
                gradePath=path, comment=comment, points=points, localText=False, localPoints=False)


e = load("05_ECHO")
a = grade(e, EX2, "a"); a["value"] = 1.0
for key in ("valueSource", "valueBeforeComments"):
    a.pop(key, None)
e["texts"].setdefault("page2", []).append(tag_element("U1", EX2 + "\\a", -0.5))
save("05_ECHO", e)

f = load("06_FOXTROT")
a = grade(f, EX2, "a"); a.update({"value": 1.5, "valueSource": "COMMENTS", "valueBeforeComments": 2.0})
grade(f, TOTAL, "Ex 2")["pointsAutoMode"] = "FULL"
f["texts"].setdefault("page2", []).append(tag_element("U2", EX2 + "\\a", -0.5))
save("06_FOXTROT", f)

os.makedirs(os.path.join(D, ".pdf4teachers"), exist_ok=True)
with open(os.path.join(D, ".pdf4teachers", "tags.yml"), "w", encoding="utf-8") as out:
    yaml.safe_dump({"tags": [{"id": "T1", "exercise": "Ex 2", "name": "Erreur formule", "kind": "MISTAKE", "points": 0.5,
                              "comment": "Erreur formule"}],
                    "copies": {
                        "05_ECHO.pdf": [{"id": "U1", "tag": "T1", "exercise": "Ex 2", "grade": EX2 + "\\a", "page": 2, "x": 40000, "y": 55000}],
                        "06_FOXTROT.pdf": [{"id": "U2", "tag": "T1", "exercise": "Ex 2", "grade": EX2 + "\\a", "page": 2, "x": 40000, "y": 55000}]}},
                   out, allow_unicode=True, sort_keys=False)

# 07_GOLF: 1.6 scored comment (method +2 from 0), no mode kept
g = load("07_GOLF"); reset(g)
grade(g, EX2, "a").update({"value": 2.0, "valueSource": "COMMENTS", "valueBeforeComments": -1.0})
g["texts"]["page2"] = [text(40000, 60000, "Bonne methode (+2)", "0x2e7d32ff", 18.0, scoredCommentId="sc-1",
                            gradePath=EX2 + "\\a", comment="Bonne methode", points=2.0, localText=False, localPoints=False)]
save("07_GOLF", g)

# 08_HOTEL: grading by lines, exercise without sub-questions, two blocks
h = load("08_HOTEL"); reset(h)
grade(h, TOTAL, "Ex 1")["value"] = 2.5
h["corrections"] = {EX1: {"text": "Correct\n\nErreur -0.5", "ids": ["h1", "", "h2"], "computed": {EX1: 2.5}}}
save("08_HOTEL", h)

print("ok", D)
