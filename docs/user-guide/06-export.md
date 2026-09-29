# Giving the copies back

## Annotated PDFs

- **File → Export (Regenerate PDF)** (`Ctrl+E`): the open copy.
- **File → Export all** (`Ctrl+Shift+E`): every copy of the files list.

Choose the destination folder and the file names (a prefix/suffix, or **Replace … to …** in the names), which kinds of
annotations to include (texts, grades, drawings, skills), the image quality (dpi), and whether to export only the
annotated copies. A copy whose destination already exists is skipped.
Another option exports only the annotations, without the content of the original PDF (to print them on the paper
copies).

Methods & mistakes pills and personal notes are never exported.

## Marks to a spreadsheet

Export icon of the Grades tab: exports the grades of this copy, or of all the listed copies (in one CSV file or one
per copy).

Options: separator (comma or semicolon) and number/formula language, only the copies with the same grade scale, of
the same folder, or fully graded; the levels of the grade scale to export; a row for the grade scale and for the
average; rows for the comments; **a column for the mark (1 to 6)**, and **Update the marks written on the copies
first**. The student's name is taken from the file name.

## Moodle feedback files

**File → Export for Moodle…** creates a zip of the annotated copies of the evaluation folder, to upload in a Moodle
assignment: each student receives their own corrected copy as a feedback file.

Once, in the Moodle assignment settings, enable the feedback types **Feedback files** and **Offline grading
worksheet**. Then:

1. In the assignment: **Grading action → Download grading worksheet**. It gives the Moodle participant id of each
   student.
2. Prepare a **students file** (CSV), one student per line: the name used in the copy file names and the email,
   e.g. `DUPONT;felix.dupont@school.ch` for `10_DUPONT.pdf`.
3. **File → Export for Moodle…**, choose the two files. The window lists each copy with its student:
   ✓ ready, ⚠ cannot be sent (not in the students file, several students match, several copies for one student, not
   in the worksheet), – participants without a copy.
4. **Create the zip**: each copy is rendered with all its annotations, named as Moodle expects.
5. In the assignment: **Grading action → Upload multiple feedback files in a zip**, and choose the zip.

## Skills (competency-based assessment)

*The Skills tab is hidden for now: this part describes it for later.*

The **Skills** tab grades competencies instead of points: create a test, list the skills and the scoring method
(characters, colors or icons), then give each skill a level on each copy. A skills chart can be placed on the copy.
Results can be exported to CSV, and tests imported from and exported to **SACoche**.
