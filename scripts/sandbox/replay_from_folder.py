#!/usr/bin/env python3
"""SANDBOX ONLY: what full_grading.py --replay types again in the app, from a folder of graded copies (their edit
files, .<copy>.pdf.yml, graded by lines): the grade scale, the text of each exercise of each copy, and the grade each
sub-question got from its lines (the reference). No name goes in it: the copies are only in their order.

    python3 -I scripts/sandbox/replay_from_folder.py <folder> <out.json>"""
import glob, json, os, sys, yaml


class Loader(yaml.SafeLoader):
    pass
Loader.add_constructor("tag:yaml.org,2002:value", lambda loader, node: loader.construct_scalar(node))


def main():
    folder, out = sys.argv[1], sys.argv[2]
    files = sorted(glob.glob(os.path.join(folder, ".*.pdf.yml")))
    scale, copies = None, []
    for path in files:
        with open(path, encoding="utf-8") as f: base = yaml.load(f, Loader=Loader) or {}
        grades = base.get("grades") or []
        children = {}
        for g in grades: children.setdefault(g.get("parentPath", ""), []).append(g)
        for kids in children.values(): kids.sort(key=lambda g: g.get("index", 0))
        root = next((g for g in grades if not g.get("parentPath")), None)
        if root is None: continue
        exercises = children.get("\\" + root["name"], [])
        this_scale = []
        for ex in exercises:
            subs = children.get(ex["parentPath"] + "\\" + ex["name"], [])
            this_scale.append([ex["name"], float(ex["total"])] if not subs else [ex["name"], [[s["name"], float(s["total"])] for s in subs]])
        scale = scale or this_scale
        corrections = base.get("corrections") or {}
        texts, expected = {}, {}
        for path_ex, entry in corrections.items():
            texts[path_ex.split("\\")[-1]] = entry.get("text", "")
            values = {g["parentPath"] + "\\" + g["name"]: float(g["value"]) for g in grades}
            for leaf, value in (entry.get("computed") or {}).items():
                # The reference: the grade the lines gave, still the grade of the copy (not typed over by hand)
                if abs(values.get(leaf, -9) - float(value)) < 1e-9: expected[leaf.replace("\\" + root["name"], "\\Total", 1)] = float(value)
        copies.append({"texts": texts, "expected": expected})
    with open(out, "w", encoding="utf-8") as f: json.dump({"scale": scale, "copies": copies}, f, ensure_ascii=False, indent=1)
    print("%d copies, %d exercise texts, %d grades to find again" % (len(copies), sum(len(c["texts"]) for c in copies),
                                                                     sum(len(c["expected"]) for c in copies)))


if __name__ == "__main__":
    main()
