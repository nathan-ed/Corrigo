#!/usr/bin/env python3
"""Copies the grading data of an evaluation folder WITHOUT the PDF and WITHOUT the names, to convert it to the
grading by lines elsewhere. Run it yourself on your computer; send only the output folder (or its zip).

    python3 scripts/anonymize_for_conversion.py "<evaluation folder>" "<output folder>" [--strip-free-texts]

Copied: the edit files of the copies (.<copy>.pdf.yml) and, from .pdf4teachers/, tags.yml (methods and mistakes) and
scoredcomments.yml (scored comments). Never copied: the PDF, notes.yml (your notes), comments.yml, marks.yml.
Each copy is renamed ELEVE_01, ELEVE_02… (in the order of the names) everywhere: file names, keys of tags.yml, and any
text containing the name of the copy. The table of the names (anonymization-map.csv) is written NEXT TO the output
folder, not in it: keep it, do not send it. With --strip-free-texts, the texts of the copies that are not grading
(no points, not a comment of a grade, not a presentation mark) are emptied: a name typed on a copy cannot go out."""
import argparse, csv, os, re, shutil, sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is needed: pip install pyyaml")

PARTS = ("tags.yml", "scoredcomments.yml")


# The name as a whole word only: a short name ("Li") is not replaced inside another word ("Limite")
def whole_word(name):
    return re.compile(r"(?<![\w])" + re.escape(name) + r"(?![\w])", re.IGNORECASE)


def replace_names(value, names):
    if isinstance(value, str):
        for real, fake in names:
            value = whole_word(real).sub(fake, value)
        return value
    if isinstance(value, dict):
        return {replace_names(k, names): replace_names(v, names) for k, v in value.items()}
    if isinstance(value, list):
        return [replace_names(v, names) for v in value]
    return value


def is_grading_text(text):
    return any(key in text for key in ("scoredCommentId", "gradeComment", "correctionLine", "pnfExercise", "mark")) \
        or str(text.get("text", "")).startswith("PNF") or re.fullmatch(r"\d+\.\s*(I\s*)*", str(text.get("text", ""))) is not None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder")
    parser.add_argument("output")
    parser.add_argument("--strip-free-texts", action="store_true")
    args = parser.parse_args()
    folder, output = os.path.abspath(args.folder), os.path.abspath(args.output)
    if output.startswith(folder + os.sep) or output == folder:
        sys.exit("The output folder must be outside the evaluation folder")
    edit_files = sorted(f for f in os.listdir(folder) if f.startswith(".") and f.endswith(".pdf.yml"))
    if not edit_files:
        sys.exit("No edit file (.<copy>.pdf.yml) in " + folder)
    os.makedirs(output, exist_ok=True)
    if os.listdir(output):
        sys.exit("The output folder must be empty")

    copies = [f[1:-len(".yml")] for f in edit_files]  # "Name.pdf"
    fake = {copy: "ELEVE_%02d.pdf" % (i + 1) for i, copy in enumerate(copies)}
    # The longest names first (a name inside another one), with and without ".pdf"
    names = sorted([(c, fake[c]) for c in copies] + [(c[:-4], fake[c][:-4]) for c in copies], key=lambda p: -len(p[0]))

    for edit_file, copy in zip(edit_files, copies):
        with open(os.path.join(folder, edit_file), encoding="utf-8") as f:
            base = yaml.safe_load(f) or {}
        if args.strip_free_texts and isinstance(base.get("texts"), dict):
            for page in base["texts"].values():
                for text in page or []:
                    if isinstance(text, dict) and not is_grading_text(text): text["text"] = ""
        base = replace_names(base, names)
        with open(os.path.join(output, "." + fake[copy] + ".yml"), "w", encoding="utf-8") as f:
            yaml.safe_dump(base, f, allow_unicode=True, sort_keys=False)
    data_dir = os.path.join(folder, ".pdf4teachers")
    for part in PARTS:
        path = os.path.join(data_dir, part)
        if not os.path.exists(path): continue
        with open(path, encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
        os.makedirs(os.path.join(output, ".pdf4teachers"), exist_ok=True)
        with open(os.path.join(output, ".pdf4teachers", part), "w", encoding="utf-8") as f:
            yaml.safe_dump(replace_names(data, names), f, allow_unicode=True, sort_keys=False)

    map_file = output.rstrip(os.sep) + "-anonymization-map.csv"
    with open(map_file, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["copy", "anonymous"])
        for copy in copies: writer.writerow([copy, fake[copy]])
    # Nothing of the names left in what goes out
    left = []
    for root, _, files in os.walk(output):
        for name in files:
            with open(os.path.join(root, name), encoding="utf-8") as f: content = f.read()
            left += [(name, real) for real, _ in names if whole_word(real).search(content)]
    if left:
        shutil.rmtree(output)
        sys.exit("A name is still in the output (removed): %s" % sorted({real for _, real in left}))
    print("%d copies anonymized into %s\nTable of the names (keep it, do not send it): %s" % (len(copies), output, map_file))


if __name__ == "__main__":
    main()
