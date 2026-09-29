# Corrigo user guide

A desktop application to correct scanned tests: annotate PDF copies, grade them exercise by exercise with a
reusable set of comments, see which methods and mistakes appear in the class, and give the copies back as PDFs or
through Moodle. The original PDFs are never modified by annotations: everything you add is kept in a separate layer
and written into new PDF files only when you export.

Corrigo is a modified version of [PDF4Teachers](https://github.com/ClementGre/PDF4Teachers), under the same license
(Apache 2.0). It is not made nor endorsed by the authors of PDF4Teachers. At its first start, Corrigo copies the
settings and lists of PDF4Teachers, if it was installed; PDF4Teachers keeps its own.

## Contents

1. [Getting started](01-getting-started.md): opening copies, the window, saving, exporting, where data is stored
2. [Annotating copies](02-annotating.md): texts and formulas, drawings and images, page tools, PDF tools
3. [Grade scale and grading](03-grading.md): creating the grade scale, exercises and pages, grading panel, comments, marks
4. [Methods and mistakes](04-methods-and-mistakes.md): tagging copies, their points and comments, pills on the copy, class overview, reviewing copies
5. [Personal notes](05-notes.md): notes and screenshots for yourself
6. [Giving the copies back](06-export.md): PDF export, marks to a spreadsheet, Moodle feedback files, skills
7. [Keyboard shortcuts](07-shortcuts.md)
8. [Settings, data and troubleshooting](08-settings-and-data.md)

## A typical correction, in short

1. Put the scanned copies of a test in one folder, one PDF per student, named like `07_DUPONT.pdf`.
2. **File → Open folder**, open the first copy.
3. In the **Grading** panel, **Create the grade scale…**: the exercises, their page, sub-questions and points,
   copied to the other copies.
4. Then grade exercise 1 on every copy: points, comments, and the methods and
   mistakes (which can add or remove points). **Next ungraded ›** (or `Z`) opens the next copy at the same exercise. Then exercise 2, and so on.
5. Look at the class overview of the methods and mistakes, check doubtful copies with the previews.
6. **Compute marks**, then export the copies (**File → Export all**) or send them through Moodle
   (**File → Export for Moodle…**).
