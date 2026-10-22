#!/usr/bin/env python3
"""Checks the edit files of an evaluation folder for the grades that Corrigo 1.6.0 / 1.7.0 may change when a copy is
opened again, or may have changed already (see docs/verification-notes.md). READ ONLY: nothing is written.

    python3 scripts/verify_grades.py <evaluation folder> [--anonymous] [--pnf-name PNF] [--json]

The edit files are the hidden files ".<copy>.pdf.yml" next to the PDF copies (setting "store the editions next to
the PDF"). With --anonymous, the copies are numbered and no comment text is printed: the report can be shared."""
import argparse, glob, json, os, sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is needed: pip install pyyaml")


# The YAML written by Corrigo (Java) may have a text "=" unquoted: read as a string, not as the YAML "value" tag
class CorrigoLoader(yaml.SafeLoader):
    pass
CorrigoLoader.add_constructor("tag:yaml.org,2002:value", lambda loader, node: loader.construct_scalar(node))


def load_yaml(f):
    return yaml.load(f, Loader=CorrigoLoader)

CHECK = "$\\Large\\checkmark$"
TAG_PREFIX = "tag:"
TABLE_X, TABLE_HEADER_Y = 3199, 130459  # PNF table of the first page (PNFAnnotationManager)
EPS = 1e-6

RISK, CHECKED, INFO = "RISQUE", "A VERIFIER", "INFO"


def num(value, default=-1.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def tier(parent_path):
    return len([s for s in (parent_path or "").split("\\") if s])


def clamp(value, total):
    return max(0.0, min(total, value))


def fmt(value):
    return "vide" if value < 0 else ("%g" % round(value, 3)).replace(".", ",")


def fmt_points(value):
    text = ("%g" % round(abs(value), 3)).replace(".", ",")
    return ("+" if value > 0 else "−" if value < 0 else "") + text


def pnf_score(marks):
    return max(0.0, 2 - .5 * max(0, marks - 1))


def check_copy(base, pnf_name):
    findings = []
    grades = [g for g in (base.get("grades") or []) if isinstance(g, dict)]
    path = lambda g: (g.get("parentPath") or "") + "\\" + str(g.get("name"))
    by_path = {path(g): g for g in grades}
    children = {}
    for g in grades:
        children.setdefault(g.get("parentPath") or "", []).append(g)

    # Placed points (methods, mistakes, criteria), PNF marks
    points, tagged, ticked = {}, set(), set()
    pnf_marks = 0
    texts = base.get("texts") or {}
    for page_key, elements in (texts.items() if isinstance(texts, dict) else []):
        for e in elements or []:
            if not isinstance(e, dict):
                continue
            if e.get("scoredCommentId") is not None:
                if e.get("noPoints") is True:
                    continue
                grade = str(e.get("gradePath", ""))
                points.setdefault(grade, []).append(num(e.get("points"), 0.0))
                if str(e.get("scoredCommentId")).startswith(TAG_PREFIX):
                    tagged.add(grade)
                if e.get("comment") == CHECK:
                    ticked.add(grade)
                continue
            text = str(e.get("text", ""))
            if text == pnf_name or text.startswith(pnf_name + " : "):
                on_table = str(page_key) == "page0" and abs(num(e.get("x"), 0) - TABLE_X) < 1000 \
                    and abs(num(e.get("y"), 0) - TABLE_HEADER_Y) < 1000
                if not on_table:
                    pnf_marks += 1

    leaves = [g for g in grades if path(g) not in children and tier(g.get("parentPath")) >= 1]

    def exercise_of(p):
        parts = p.split("\\")
        return p if len(parts) <= 3 else "\\" + parts[1] + "\\" + parts[2]

    for g in leaves:
        p, total, value = path(g), num(g.get("total"), 0), num(g.get("value"))
        computed = g.get("valueSource") == "COMMENTS"
        before, start = num(g.get("valueBeforeComments")), num(g.get("pointsStart"))
        pts = points.get(p, [])
        s = sum(pts)
        ex = by_path.get(exercise_of(p), {})
        auto = ex.get("pointsAutoMode")

        # 1. Grade typed by hand over points of methods / mistakes: they are counted again at the next opening
        if not computed and value >= 0 and p in tagged and pts:
            predicted = clamp((0 if p in ticked else value) + s, total)
            if abs(predicted - value) > EPS:
                findings.append((RISK, p, "1", "note tapée à la main %s ; à la prochaine ouverture elle deviendra %s "
                                 "(les points des pastilles, %s, sont recomptés)" % (fmt(value), fmt(predicted), fmt_points(s))))
        # 1b. Possibly changed already: computed from a typed value plus the points
        if computed and before >= 0 and p in tagged and pts and p not in ticked and abs(s) > EPS:
            findings.append((CHECKED, p, "1b", "note affichée %s = valeur tapée %s %s des pastilles. Si tu as tapé "
                             "%s APRÈS avoir posé les pastilles, la bonne note est %s (bug) ; si tu l'as tapée AVANT, %s "
                             "est juste." % (fmt(value), fmt(before), fmt_points(s), fmt(before),
                                             fmt(before), fmt(value))))
        # 3. Copy of 1.6.0: a method placed on an empty sub-grade counted from the maximum
        if computed and auto == "FULL" and start < 0 and before < 0 and p not in ticked and pts \
                and all(x >= 0 for x in pts) and any(x > 0 for x in pts) and abs(value - total) < EPS and s < total - EPS:
            findings.append((CHECKED, p, "3", "seulement des méthodes (%s) mais la note est le maximum %s : elle part "
                             "du maximum (mode par erreurs). Par méthodes, elle vaudrait %s." % (fmt_points(s), fmt(total),
                                                                                                fmt(clamp(s, total)))))
        # 4. Start kept from an older version different from the value typed
        if computed and start >= 0 and before >= 0 and abs(start - before) > EPS and p not in ticked:
            findings.append((CHECKED, p, "4", "départ gardé d'une ancienne version %s ≠ valeur tapée %s : au prochain "
                             "point placé dans l'exercice, la note peut repartir de %s." % (fmt(start), fmt(before),
                                                                                         fmt(start))))
        if not computed and start >= 0:
            findings.append((INFO, p, "4b", "reste d'un ancien départ (%s) sur une note tapée : sans effet tant qu'aucun "
                             "point n'est ajouté à cette sous-note." % fmt(start)))

    # 5. By criteria started by a comment at 0 point: empty sub-grades set to 0
    for ex_path, ex in by_path.items():
        if tier(ex.get("parentPath")) != 1 or ex.get("pointsAutoMode") != "ZERO":
            continue
        ex_leaves = [g for g in leaves if exercise_of(path(g)) == ex_path]
        all_pts = [x for g in ex_leaves for x in points.get(path(g), [])]
        if not any(x > 0 for x in all_pts) and not any(path(g) in ticked for g in ex_leaves):
            zeros = [path(g) for g in ex_leaves if g.get("valueSource") == "COMMENTS" and not points.get(path(g))]
            findings.append((CHECKED, ex_path, "5", "mode « par critères » sans méthode ni critère (un commentaire à 0 "
                             "point placé en premier ?) : sous-notes mises à 0 : %s" % (", ".join(zeros) or "aucune")))

    # 2. Presentation grade (PNF): set from the marks at the next opening
    for ex_path, ex in by_path.items():
        if tier(ex.get("parentPath")) != 1 or str(ex.get("name", "")).strip().lower() != pnf_name.lower() or ex_path in children:
            continue
        value, total = num(ex.get("value")), num(ex.get("total"), 0)
        started = any(num(g.get("value")) >= 0 for g in leaves if path(g) != ex_path)
        if pnf_marks == 0 and value < 0 and not started:
            continue
        expected = min(pnf_score(pnf_marks), total)
        if abs(expected - value) > EPS:
            findings.append((RISK, ex_path, "2", "note %s ; %d marque(s) %s sur la copie : à la prochaine ouverture elle "
                             "deviendra %s" % (fmt(value), pnf_marks, pnf_name, fmt(expected))))
        elif pnf_marks == 0:
            findings.append((CHECKED, ex_path, "2b", "note %s avec 0 marque : si tu avais tapé une autre note, elle a été "
                             "remplacée par %s à une ouverture." % (fmt(value), fmt(expected))))

    # 6. Sums of the parents
    for parent_path, kids in children.items():
        parent = by_path.get(parent_path)
        if parent is None:
            continue
        values = [num(k.get("value")) for k in kids if num(k.get("value")) >= 0]
        expected = sum(values) if values else -1.0
        if abs(num(parent.get("value")) - expected) > EPS:
            findings.append((CHECKED, parent_path, "6", "somme incohérente : %s enregistré, %s pour ses sous-notes"
                             % (fmt(num(parent.get("value"))), fmt(expected))))
    return findings


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder")
    parser.add_argument("--anonymous", action="store_true", help="numbered copies, no comment text")
    parser.add_argument("--pnf-name", default="PNF", help="name of the presentation exercise (setting)")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    files = sorted(glob.glob(os.path.join(args.folder, "**", ".*.pdf.yml"), recursive=True))
    if not files:
        sys.exit("No edit file (.<copy>.pdf.yml) in " + args.folder + ": are the editions stored next to the PDF?")
    report, counts = [], {RISK: 0, CHECKED: 0, INFO: 0}
    for i, file in enumerate(files, 1):
        name = "Copie %02d" % i if args.anonymous else os.path.relpath(file, args.folder)
        try:
            with open(file, encoding="utf-8") as f:
                base = load_yaml(f) or {}
        except Exception as e:  # noqa: BLE001
            report.append({"copy": name, "level": CHECKED, "grade": "", "rule": "0", "text": "illisible : %s" % (type(e).__name__ if args.anonymous else e)})
            counts[CHECKED] += 1
            continue
        for level, grade, rule, text in check_copy(base, args.pnf_name):
            report.append({"copy": name, "level": level, "grade": grade, "rule": rule, "text": text})
            counts[level] += 1

    if args.json:
        print(json.dumps({"copies": len(files), "counts": counts, "findings": report}, ensure_ascii=False, indent=1))
        return
    print("%d copies lues (lecture seule). %d RISQUE, %d A VERIFIER, %d INFO.\n"
          % (len(files), counts[RISK], counts[CHECKED], counts[INFO]))
    current = None
    for item in report:
        if item["copy"] != current:
            current = item["copy"]
            print("== " + current)
        print("  [%s] règle %s · %s : %s" % (item["level"], item["rule"], item["grade"], item["text"]))
    if not report:
        print("Rien à signaler.")


if __name__ == "__main__":
    main()
