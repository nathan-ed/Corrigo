# Grading features: development notes

State of the grading-related work on this fork, to pick it up later.
Last update: 2026-09-29, `master` (methods & mistakes with points and comment, replacing the scored comments; compact grading panel; rebranding; HTML user guide).

## What exists

The files written for Corrigo are in the `corrigo` package, with the same sub-packages as the PDF4Teachers code they sit next to (`corrigo.panel.sidebar.grades.tags`…); upstream files stay in `fr.clementgre.pdf4teachers`.

| Feature | Where to use it | Main code |
|---|---|---|
| Grading panel (compact per-sub-grade sections, next ungraded copy) | Grading tab (list icon) | `panel/sidebar/grades/GradingPanel.java` |
| Jump to an exercise page (buttons + Alt+1…9) | Grading panel header | `GradingPanel.updateExerciseJumps`, `FooterBar.goToExercise` |
| Marks (Swiss 1–6) and Moodle feedback export | Grading panel / File menu | `grades/Marks*.java`, `grades/export/Moodle*.java` |
| Personal notes and screenshots (Ctrl+Shift+N, Ctrl+Shift+S) | Notes tab (book icon) | `panel/sidebar/notes/` |
| Evaluation folder storage | automatic | `datasaving/evaluation/EvaluationFolders.java` |
| Comments of the evaluation grouped by exercise | Texts tab, "This evaluation" | `panel/sidebar/texts/evaluation/` |
| "Where is it written?" page previews with zoom | Right-click a comment | `CommentUsages`, `CommentUsagesWindow`, `PagePreview` |
| Formulas in texts (`$…$`, `$$…$$` still read) | Texts tab | `utils/MathText.java`, `editions/elements/MixedTextRenderer.java` |
| Methods & mistakes tags, class overview, review mode (#, Ctrl+Shift+M) | Grading panel card | `panel/sidebar/grades/tags/` |
| Pills on the copy (methods & mistakes, not printed, draggable) | Pages of the open copy | `tags/TagMarkers.java` |
| Change a tag without opening the copy (right-click a preview) | "Show the copies" windows | `ExerciseTagsCard.buildTagCardMenu`, `CommentUsagesWindow.Actions` |
| Comment suggestions (this field, then this exercise, then the others) | Grading panel comment fields | `grades/CommentSuggestions.java`, `CommentBank.suggest` |
| Tab / Shift+Tab: points, comment, next points… general comment | Grading panel | `GradingPanel.focusField` |

Sidebar order: files, texts, notes, grading, grades, skills, paint (`SideBar.DEFAULT_LEFT_TABS`).

## Data storage

Everything about an evaluation is stored in a hidden folder next to its copies:

```
<evaluation folder>/
  01_XXX.pdf, .01_XXX.pdf.yml        copies and their editions (storeEditionsNextToPdf)
  .pdf4teachers/
    comments.yml       comments by exercise (CommentBank): text, exercise, movedTo, hidden, style, copies, pages, fields (copy -> grade path), field
    scoredcomments.yml scored comments catalogs by evaluation signature (now only "from max" / "from 0" per grade)
    notes.yml          notes (copy stored by file name, relative to the folder)
    notes/*.png        note screenshots
    tags.yml           methods & mistakes: tags (id, exercise it was created for, name, kind, [points, comment, gradePoints]) + per copy: uses (id, tag, exercise, grade, page, x, y, [labelX, labelY])
```

- `EvaluationFolders` activates the folder of the opened PDF (`MainScreen.openFile`), saves the previous one first, then loads each registered `Part`. The active folder is set **before** loading (a part may save while loading).
- Writes are atomic (tmp file + move), debounced 1 s, flushed on folder switch and on close (`requestCloseApp`, `UserData.save` on the FX thread only). The data folder is only created when there is something to save.
- `Config(File)` **creates the file**: only use it for files that exist.
- App-wide data (`~/.local/share/PDF4Teachers/`): notes taken with no copy open (`teachernotes.yml` + `notes/`), `scoredcomments.yml` (backup and migration source), `evaluations.yml` (evaluation folders opened, for the cross-evaluation search).
- Migrations: app-wide notes move into their evaluation folder when it is opened (all or nothing, old screenshots kept); scored comments catalogs are copied into the folder after the edition is loaded (`ScoredComments.onEditionLoaded`).

- New evaluation (`NewEvaluationWizard`, pure part `NewEvaluationPlan`): scan → students → pages per copy (top of each first page shown) → scale (`GradeScaleSetupDialog`, or ratings from a .yml / another copy's edition written with `GradeCopyGradeScaleDialog.copyToFile`). Never writes over existing copies.
- Exercise pages: no footer controls any more; the page of an exercise is the page of its first leaf grade, unless set in the panel menu (Plus ▾ → Pages des exercices…). `FooterBar.isExerciseCorrectionMode()` = grading tab selected; `areExercisePagesUnknown()` drives the panel banner.
- Hidden tabs: `SideBar.HIDDEN_TABS` (paint, skills), code kept.

## Key rules and design decisions

- **Exercise of a comment** (`ExerciseLocator`): comment written for a grade → that exercise; else closest sub-grade above on the same page (2 % tolerance), or first below; else the exercise pages set in the footer bar; else last sub-grade on a previous page; else "General". Only leaf grades count (the totals table is ignored).
- Comment bank entries are keyed by exercise + text with whitespace normalized; comments no longer on any copy are kept (can be hidden via right-click).
- **Math**: `$…$` inline; `$$` with no closing makes the rest math (old texts); `\$` is a dollar; a lone `$` is text; texts with `&&` (old LibreOffice math) keep the old rendering. The "default input mode" setting is hidden and no longer used.
- Mixed text + formulas is rendered as one image (same on screen and in the PDF export), wrapped at the element max width, text in its own font with a per-character fallback.
- Tags: two kinds (METHOD, MISTAKE). A tag is created for an exercise but usable in any: the card and the `#` picker list the tags of the exercise (created for it or used in it) first, then all the others. Names are unique in the evaluation (old per-exercise duplicates are merged when read). A use (occurrence, with an id) is (copy, exercise, tag, grade, placement): the same tag can be on a copy several times, in the same exercise or in several. The name is never written on the copies.
- Placement: the `#` picker stores the spot under the mouse (pill in the left margin at its height, a dot on the spot); chips store `ExerciseTags.getExercisePlacement()` (recognized by `isExerciseSpot`: pill under the grade of the exercise). `#` adds a new occurrence at the mouse, or removes the occurrence pointed at. A dragged pill keeps its own position (labelX/Y); dragging the dot moves the spot. The dot grab area and the pill text keep a minimum size on screen (`TagMarkers.Layer.getPixel`).
- Points and comment (`TagScoringDialog`, `EvaluationTags.getTargets`): optional points (positive; the kind gives the sign) and comment per tag. Each occurrence counts on its `grade` (the sub-grade whose grade is nearest the spot on the same page, else the active one), or, if the tag has `gradePoints` for that exercise, on each of those sub-grades (comment on the first). A target is written as a `ScoredCommentElement` with id `tag:<tagId>#<useId>[@gradePath]` (`TagEditFiles.elementId`): points only, or "comment (points)", or the comment only with `KEY_NO_POINTS` (not counted). `TagScoring.sync` updates the open copy (JavaFX elements) and the other copies (their edit files, `TagEditFiles.sync`) and recomputes the grades; the grade typed before the first counted element is kept (`KEY_VALUE_BEFORE` / `valueBeforeComments`) and restored when none is left. Deleting such an element by hand removes the occurrence. The old scored comments UI is gone (Grades tab list, panel items, N key, "make it a scored comment"); the engine stays.
- Overview: "This exercise" (tags of the exercise, average points) or "Whole evaluation" (copies having it anywhere, count by exercise).
- Comment suggestions: `CommentBank` remembers the grade field each comment was written for. Ranking: this field, then this exercise, then the others; most used first. Texts typed live and replaced in the same field are forgotten (`updateCopy`), the pending scan is flushed before suggesting.
- Review mode hooks into `FileTab.openNeighborFile`: every previous/next copy shortcut goes through the reviewed copies only.
- Test data and commits must never contain real student names or identifiers.

## Testing

- Unit tests (147): `./gradlew test`. Pure logic has tests: `MathTextTest`, `ExerciseLocatorTest`, `CommentBankTest`, `EvaluationFoldersTest`, `EvaluationTagsTest`, `TagEditFilesTest`, `MarksTest`, `MoodleFeedbackTest`…
- GUI tests: never on the real display, data or evaluation folders.
  1. `python3 scripts/make_test_eval.py <dir>` builds a fictional evaluation (copies ALPHA…HOTEL in `Classe-A/Test-1`, 2 copies in `Classe-B/Test-1`; exercises with different methods and typical mistakes, grades and a few comments).
  2. `Xvfb :77 -screen 0 1600x1000x24 &`, then `DISPLAY=:77 XDG_DATA_HOME=<tmp>/xdg ./gradlew --init-script <tmp>/isolated-build.gradle run` with the init script setting `allprojects { layout.buildDirectory.set(file("<tmp>/build")) }` (do not rebuild the jar the real app runs from).
  3. In `<tmp>/xdg/PDF4Teachers/settings.yml` set `storeEditionsNextToPdf: true` and `checkUpdates: false`; put the fictional copies in `userdata.yml` → `files.lastFiles` (YAML flow list: edit with python yaml).
  4. Drive with `xdotool`, screenshots with `import -window root`. Mouse wheel and slider drags do not work there: use the scroll bar arrows and keyboard.
  5. Search the log with `grep -a` (it has color codes; plain grep sees a binary file and hides exceptions).

## Known issues / not done

- Existing, unrelated: loading skills data logs an `IllegalArgumentException` (empty `NotationType`) on a fresh install; `AutoTipsManager.hideAll()` can throw `ConcurrentModificationException` (not called by the new code).
- Old `&&` comments show their `&&` in the lists (that is how they are stored).
- Previews show the PDF page plus the one comment or tag marker, not the other annotations of the copy.
- The cross-evaluation search only knows folders opened since this storage exists, plus the folders of the files list.
- Tags whose copy was renamed stay attached to the old file name (`EvaluationTags.retainCopies` exists but is not called).
- Mixed text + formula elements cannot be resized with the grab line (their width is the element max width).

## Ideas for next steps

- Export of the tags (CSV per student: methods and mistakes per exercise), or show them in the Moodle feedback.
- Filter the files list by tag; tags across evaluations (same exercise in another class).
- Suggest a tag from the comments written near the same spot.
- Drag a copy from the "without method" previews onto a tag to classify it.
