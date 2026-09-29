# Annotating copies

Everything described here is an annotation: it is shown on the copy, kept in the copy's edit file, and written into
the PDF only when you export. When grading, most comments are better written from the
[grading panel](03-grading.md#the-grading-panel-list-icon), which places them next to the grade and suggests the comments already
used; the tools below remain for free annotations.

## Texts (T tab)

- **Add a text**: double-click on the page, or `Ctrl+T` at the mouse position. Type in the text field of the Texts tab.
- **Move / resize**: drag the text. The solid line on its right sets its maximum width (it wraps beyond).
- **Style**: font (including system fonts), size, bold, italic and color in the Texts tab. `Ctrl+Alt++` / `Ctrl+Alt+-`
  change the size of the selected text; `Ctrl+Alt+1…9` change its color.
- **Formulas**: write math between `$…$`, as in LaTeX: `the root is $x = \frac{-b}{2a}$`. The **∑** button makes the
  whole text a formula. Old texts written with `$$` or LibreOffice Math `&&` still display.
- **Links**: a text starting with `www.`, `http://` or `https://` becomes a clickable link in the exported PDF.
- **Duplicate**: double-click a text. **Send to page…** (right-click) moves it to another page.
- **Copy to files**: puts the selected text on the other open copies, at the same page and position.

### Comment lists

The Texts tab keeps lists of the texts you wrote, to insert them again:

- **This evaluation**: the comments written on the copies of the evaluation, grouped by exercise (read from all the
  copies of the folder). The number shows on how many copies a comment is. Right-click a comment to:
  - **Where is it written?**: previews of all the copies where it is, zoomable around the comment; click one to open
    the copy at that page;
  - **Move to exercise**: when it was listed under the wrong exercise;
  - **Remove from this list**: hides it (it stays on the copies).
- **Favorite Elements**: your reusable texts. `Ctrl+1…9` inserts one of the first nine at the mouse.
- **Previous Elements**: the texts you wrote recently.
- **Current Document Elements**: the texts of the open copy.

Typing in the text field highlights the matching texts of the lists; use the arrows and `Enter` to insert one. Click a
list element to insert it; `Shift+click` inserts it *linked* (changing it in the list changes it on the page). The
lists can be sorted, saved and reloaded (save and list icons).

## Drawings, shapes and images (Paint tab)

- **Freeform drawing**: **New freeform line**, or `Ctrl+D` to start a drawing on the page. In drawing mode, `Shift`
  (or hold `L`) draws straight lines, `M` (or hold `P`) horizontal/vertical ones; `Backspace` undoes the last stroke;
  `Esc`, a right-click or a double-click leaves the mode. Long handwriting is split into several elements (settings
  **Handwriting**).
- **Vector shapes**: favorite figures, previous figures, **Load default geometric figures**, or open an SVG file.
- **Images**: favorite images, and a **Gallery** of the image folders you add to it.
- A figure or image can get its own keyboard shortcut (right-click → **Edit the keyboard shortcut**).

## Selecting and editing elements

Click an element to select it; `Ctrl+A` selects all the elements of the page, `Delete` removes the selection.
`Ctrl+X`, `Ctrl+C` / `Ctrl+V` cut, copy and paste. Right-click an element for its menu (favorites, delete, duplicate,
send to page…).

## Pages (Edit pages mode)

Each page has side buttons: move up/down, rotate left/right, delete, add pages (blank, from another PDF, or converted
images), capture as an image, crop or add margins. **Edit pages** in the footer bar shows the pages as a grid where you
can select several (`Ctrl+click`, `Shift+click`) and drag them to reorder.

These actions change the PDF file itself, immediately (undo them with `Ctrl+Z` while in Edit pages mode).

## PDF tools

In the **Tools** menu (and its **PDF tools** sub-menu):

- **Convert image files to PDF** (`Ctrl+Shift+C`): images to one PDF, or each sub-folder of a folder to its own PDF
  (one page per image), with the page format and resolution of your choice. Also used to add converted pages to a
  copy.
- **Split a PDF** (three entries in **PDF tools**): by marker pages of a color (insert dark sheets between the students when scanning), every *n* pages,
  or at the pages you select, and name the resulting files (names can be imported from a file).
- **Booklet** (**PDF tools**): assemble a booklet, or undo one (with options for exam double sheets and reversed order).
- **Margins** (**PDF tools**): add margins around the pages, or negative margins to crop them.
- **Capture** (side button of a page): export a page, a selection or the whole document as PNG images.
- **Export/Import edits or marking scales**: save the annotations or the grade scale of copies to a file, and load
  them onto other copies.
- **Edits of documents with same name**: recover the annotations of a copy that was moved or renamed outside the app.
- **Delete edits on all opened files**: removes all the annotations of the listed copies (the PDFs are not changed).
