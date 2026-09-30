# Corrigo user guide

A desktop application to correct scanned tests: annotate PDF copies, grade them exercise by exercise with a
reusable set of comments, see which methods and mistakes appear in the class, and give the copies back as PDFs or
through Moodle. The original PDFs are never modified by annotations: everything you add is kept in a separate layer
and written into new PDF files only when you export.

Corrigo is a modified version of [PDF4Teachers](https://github.com/ClementGre/PDF4Teachers), under the same license
(Apache 2.0). It is not made nor endorsed by the authors of PDF4Teachers. At its first start, Corrigo copies the
settings and lists of PDF4Teachers, if it was installed; PDF4Teachers keeps its own.

## Demo videos

- [A new evaluation from the scan](https://github.com/nathan-ed/PDF4Teachers/blob/master/docs/videos/new-evaluation-en.mp4)
- [Grading an exercise](https://github.com/nathan-ed/PDF4Teachers/blob/master/docs/videos/grading-en.mp4)
- [The class, previews, review and notes](https://github.com/nathan-ed/PDF4Teachers/blob/master/docs/videos/class-en.mp4)

## Contents

0. [Why Corrigo](00-why-corrigo.md): what Corrigo adds to PDF4Teachers, and what it brings compared to Moodle

1. [Getting started](01-getting-started.md): opening copies, the window, saving, exporting, where data is stored
2. [Annotating copies](02-annotating.md): texts and formulas, drawings and images, page tools, PDF tools
3. [Grade scale and grading](03-grading.md): creating the grade scale, exercises and pages, grading panel, comments, marks
4. [Methods and mistakes](04-methods-and-mistakes.md): tagging copies, their points and comments, pills on the copy, class overview, reviewing copies
5. [Personal notes](05-notes.md): notes and screenshots for yourself
6. [Giving the copies back](06-export.md): PDF export, marks to a spreadsheet, Moodle feedback files, skills
7. [Keyboard shortcuts](07-shortcuts.md)
8. [Settings, data and troubleshooting](08-settings-and-data.md)

## A typical correction, in short

1. Scan all the copies into one PDF, in class order.
2. **File → New evaluation…** ([details](01-getting-started.md#a-new-evaluation-from-the-scan)): the scan, the names
   of the students in the same order, the pages of each copy, then the grade scale (created with the assistant:
   exercises, their page, sub-questions or criteria and points, or imported from another evaluation). It writes one PDF per
   student, named like `07_DUPONT.pdf`, and opens the first one in the grading panel.
3. Then grade exercise 1 on every copy: points, comments, and the methods and
   mistakes (which can add or remove points). **Next ungraded ›** (or `Z`) opens the next copy at the same exercise. Then exercise 2, and so on.
4. Look at the class overview of the methods and mistakes, check doubtful copies with the previews.
5. **Compute marks**, then export the copies (**File → Export all**) or send them through Moodle
   (**File → Export for Moodle…**).
