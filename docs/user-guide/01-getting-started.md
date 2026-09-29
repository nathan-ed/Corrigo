# Getting started

## The window

- **Menu bar**: File, Edit (undo/redo), View (sidebars, full screen), Tools, Help, Settings….
- **Sidebars**: tabs on the left (and right) of the document. Each tab is an icon:

  | Icon | Tab | Used for |
  |---|---|---|
  | PDF sheet | **Files** | the list of copies you are working on |
  | List | **Grading** | the grading panel: grade one exercise at a time |
  | Book | **Notes** | your personal notes (never on the copies) |
  | **T** | **Texts** | text annotations and comment lists |
  | **/20** | **Grades** | the grade scale and the grade exports |

  The **Paint** (drawings, shapes, images) and **Skills** tabs are hidden for now.

  Drag a tab by its icon to move it to the other sidebar. **View** can minimize the sidebars, restore their widths or
  move all tabs to one side.
- **Document**: the open copy. `Ctrl` + scroll zooms; right-click a page for quick actions (a method or a mistake
  here, a personal note, a screenshot with a note, the next grade, favorite texts).
- **Footer bar**: zoom, **Edit pages** mode, column/grid view, the number of elements of the copy, the total, and the
  save status.

## A new evaluation from the scan

**File → New evaluation…** (or the button of the empty grading panel) makes the copies of a test from the scan of all
of them, in one PDF:

1. **The scan**: choose the PDF. It is not changed. The copies go in a new folder named after it, next to it (you can
   choose another one). A folder that already has copies is never written over: the assistant asks for another one.
2. **The students**: their names, one per line, in the order of the copies in the scan (paste a column of a
   spreadsheet if you like; a line `12;Name` keeps the name). Copies are numbered (`01_`, `02_`…) and in capitals,
   both optional: `07_DUPONT.pdf`.
3. **The pages of each copy**: guessed from the scan and the number of students. The top of the first page of each
   copy is shown with its file name: check that each one is the right student. The assistant says when the scan has
   too few or too many pages.
4. **The grade scale**: create it now with the assistant (see [Creating the grade scale](03-grading.md#creating-the-grade-scale)),
   import it (an exported grade scale `.yml`, or a copy of the same test in another evaluation, whose grades are
   placed at the same spots), or later.

The copies are then listed in the Files tab, and the first one opens in the grading panel at the first exercise.

## Opening copies

- **File → Open file(s)** (`Ctrl+O`) adds PDF files to the Files tab; **File → Open folder** (`Ctrl+Shift+O`) adds
  all the PDFs of a folder.
- Double-click a file to open it. `Ctrl+Alt+←/→` opens the previous/next file of the list.
- Right-click a file: open, rename, create a copy, remove it from the list (the file stays on disk), delete it.
- Sort the list with the buttons at the top (date added, edit state, name, folder).
- Rename copies from the app (**File → Rename the PDF file** or the file menu): the annotations follow. Renamed with a
  file explorer, a copy loses them (see **Tools → Edits of documents with same name** to recover them).

### Organising an evaluation

Keep one folder per evaluation, containing only its copies, one PDF per student. Name the copies with a number and the
student's name, e.g. `07_DUPONT.pdf`: the name after the underscore identifies the student for the Moodle export.
Everything the app knows about the evaluation (comments, methods and mistakes, notes) is stored in this folder, in a
hidden `.pdf4teachers` sub-folder, so the folder can be moved or backed up as a whole.

Scanned copies can be prepared with the PDF tools: split one big scan into one file per student, convert photos to
PDF, rotate or reorder pages (see [Annotating copies](02-annotating.md#pdf-tools)).

## Saving

Annotations are saved automatically when you switch copy or close the app (setting **Autosave the edition**), and
every few minutes if **Periodical autosave** is on. **File → Save edits** (`Ctrl+S`) saves now. The footer bar shows
"Saved!".

The annotations of `copy.pdf` are stored next to it in a hidden file `.copy.pdf.yml` (setting **Store the edits next
to the PDF files**, recommended), or else in the app's data folder.

**Page changes are different**: rotating, moving, adding or deleting pages, margins, cropping and booklets change the
PDF file itself, immediately.

## Exporting

**File → Export (Regenerate PDF)** (`Ctrl+E`) writes a new PDF with all the annotations; **File → Export all** (`Ctrl+Shift+E`)
does it for every file of the list. The original PDFs are not changed. See [Giving the copies back](06-export.md).

## Undo

`Ctrl+Z` / `Ctrl+Shift+Z` undo and redo the changes of the document's annotations. In **Edit pages** mode they undo page
changes instead.
