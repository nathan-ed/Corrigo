# Corrigo: development notes

State of the grading-related work on this fork, to pick it up later.
Last update: 2026-09-29, `master` at `d78bd679` (renamed Corrigo; new evaluation from a scan; scale assistant with criteria; guided tour; marks scales; demo videos and screenshots).

## What exists

The files written for Corrigo are in the `corrigo` package, with the same sub-packages as the PDF4Teachers code they sit next to (`corrigo.panel.sidebar.grades.tags`…); upstream files stay in `fr.clementgre.pdf4teachers`.

| Feature | Where to use it | Main code |
|---|---|---|
| Grading panel (compact per-sub-grade sections, next ungraded copy) | Grading tab (list icon) | `panel/sidebar/grades/GradingPanel.java` |
| Jump to an exercise page (buttons + Alt+1…9) | Grading panel header | `GradingPanel.updateExerciseJumps`, `FooterBar.goToExercise` |
| Marks and Moodle feedback export | Grading panel More ▾ / File menu | `grades/Marks*.java`, `grades/export/Moodle*.java` |
| Marks scale per evaluation: proportional (min, max), points → mark table, formula of p and t; rounding step; presets | More ▾ → Marks scale… | `grades/MarkScale.java` (pure, with `Formula` parser), `MarkScaleDialog.java`, `Marks.FOLDER_PART` |
| New evaluation from the scan of the class (students, pages per copy, scale) | File → New evaluation…, empty start screen | `grades/NewEvaluationWizard.java`, `NewEvaluationPlan.java` |
| Grade scale assistant (exercises, pages, sub-questions or named criteria, points) | Empty grading panel, new evaluation step 4 | `grades/GradeScaleSetupDialog.java` |
| Welcome window (French / English) and guided tour | First opening; Help → Guided tour | `corrigo/interfaces/WelcomeWindow.java`, `GuidedTour.java` |
| Personal note from a right-click (page or copy preview) | Page menu, preview menus | `PageRenderer.showContextMenu`, `CommentUsagesWindow.getNoteItems` |
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

Sidebar order: files, grading, notes, texts, grades (`SideBar.DEFAULT_LEFT_TABS`; the previous default order is migrated). Paint and skills are hidden (`SideBar.HIDDEN_TABS`, code kept; their page-menu items are hidden too).

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
    marks.yml          marks scale (kind, min, max, step, table, formula); not written for the Swiss default
```

The hidden folder keeps the name `.pdf4teachers` (renaming it would lose existing data).

- `EvaluationFolders` activates the folder of the opened PDF (`MainScreen.openFile`), saves the previous one first, then loads each registered `Part`. The active folder is set **before** loading (a part may save while loading).
- Writes are atomic (tmp file + move), debounced 1 s, flushed on folder switch and on close (`requestCloseApp`, `UserData.save` on the FX thread only). The data folder is only created when there is something to save.
- `Config(File)` **creates the file**: only use it for files that exist.
- App-wide data (`~/.local/share/Corrigo/`, `%APPDATA%\Corrigo`, `~/Library/Application Support/Corrigo`; copied once from the PDF4Teachers folder at the first start, `PlatformUtils.getDataFolder`): notes taken with no copy open (`teachernotes.yml` + `notes/`), `scoredcomments.yml` (backup and migration source), `evaluations.yml` (evaluation folders opened, for the cross-evaluation search).
- Migrations: app-wide notes move into their evaluation folder when it is opened (all or nothing, old screenshots kept); scored comments catalogs are copied into the folder after the edition is loaded (`ScoredComments.onEditionLoaded`).

- New evaluation (`NewEvaluationWizard`, pure part `NewEvaluationPlan`): scan → students → pages per copy (top of each first page shown) → scale (`GradeScaleSetupDialog`, or ratings from a .yml / another copy's edition written with `GradeCopyGradeScaleDialog.copyToFile`). Never writes over existing copies.
- Exercise pages: no footer controls any more; the page of an exercise is the page of its first leaf grade, unless set in the panel menu (Plus ▾ → Pages des exercices…). `FooterBar.isExerciseCorrectionMode()` = grading tab selected; `areExercisePagesUnknown()` drives the panel banner.
- Hidden tabs: `SideBar.HIDDEN_TABS` (paint, skills), code kept.
- First opening (`Main.firstLaunch`, no settings.yml): `WelcomeWindow` sets the language, then the main window starts and `GuidedTour.start()` runs. The tour is an overlay pane in `MainWindow.tourLayer` (a StackPane over the scene root), not a Popup: popups with transparency show as white frames on X without compositing. The user guide no longer opens by itself.
- Exercise navigation (`FooterBar.selectExercise`, `goToExercise`) applies the exercise directly (`applySelectedExercise`); the hidden footer ComboBox is only kept in sync.
- Digit shortcuts use the typed character (`MathUtils.parseDigitTypedOrNull`): Shift+3 / AltGr+3 typing `#` must not also put the 3rd tag.
- Comment typing performance: suggestions scan the copy once per focus, `CommentBank.Entry` caches its simplified text, the Texts tab list is rebuilt only when that tab is shown, and the Texts tab autocomplete runs only when its own field is focused.

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

- Unit tests (159): `./gradlew test`. Pure logic has tests: `MathTextTest`, `ExerciseLocatorTest`, `CommentBankTest`, `EvaluationFoldersTest`, `EvaluationTagsTest`, `TagEditFilesTest`, `MarksTest`, `MarkScaleTest`, `NewEvaluationPlanTest`, `MoodleFeedbackTest`…
- GUI tests: never on the real display, data or evaluation folders.
  1. `python3 scripts/make_test_eval.py <dir>` builds a fictional evaluation (copies ALPHA…HOTEL in `Classe-A/Test-1`, 2 copies in `Classe-B/Test-1`; exercises with different methods and typical mistakes, grades and a few comments).
  2. `Xvfb :77 -screen 0 1600x1000x24 &`, then `DISPLAY=:77 XDG_DATA_HOME=<tmp>/xdg ./gradlew --init-script <tmp>/isolated-build.gradle run` with the init script setting `allprojects { layout.buildDirectory.set(file("<tmp>/build")) }` (do not rebuild the jar the real app runs from).
  3. In `<tmp>/xdg/Corrigo/settings.yml` set `storeEditionsNextToPdf: true` and `checkUpdates: false`; put the fictional copies in `userdata.yml` → `files.lastFiles` (YAML flow list: edit with python yaml).
  4. Drive with `xdotool`, screenshots with `import -window root`. Mouse wheel and slider drags do not work there: use the scroll bar arrows and keyboard.
  5. Search the log with `grep -a` (it has color codes; plain grep sees a binary file and hides exceptions).
  6. GTK file choosers don't get the keyboard: find the chooser window, `xdotool windowfocus` it, then `ctrl+l`, the path, Return. Dialog windows can fall behind the main window (no window manager): raise them with `xdotool windowraise`.
  7. Never start a window manager or other session-aware program on the test displays: the shell's `SESSION_MANAGER` belongs to the real desktop session. Run the app with `env -u SESSION_MANAGER -u DBUS_SESSION_BUS_ADDRESS … dbus-run-session -- ./gradlew --no-daemon run`.

## Demo videos and screenshots

- `docs/videos/*.mp4` (+ animated `.webp` previews for the README) and `docs/images/{en,fr}/*.png`, all from a fictional class (students named after mathematicians).
- `scripts/make_demo_scan.py scan OUT.pdf --lang en|fr` makes a handwritten-looking scan of 12 copies; `graded DIR --lang … --graded N` makes the split copies with a scale, the first N graded, methods and mistakes. Needs the Kalam font (OFL), downloaded into `~/.cache/corrigo-demo`.
- They were recorded on a separate 1920×1080 Xvfb (`:78`) with a fake home (`teacher/Documents/Evaluations/2025-2026/…`), dark theme, `storeEditionsNextToPdf: true`, `XCURSOR_THEME=breeze_cursors XCURSOR_SIZE=36`, driven by small Python scripts (smooth mouse, human typing, captions and click rings added with ffmpeg: `drawtext` + an `overlay` ring). The driver scripts lived in the session scratchpad and are not in the repo; the positions depend on the language (French texts wrap differently).
- `scripts/build_user_guide.py` embeds the screenshots in the HTML/PDF guides as data URIs (links to .png/.webp/.mp4 are not rewritten as chapter anchors).

## Known issues / not done

- Existing, unrelated: loading skills data logs an `IllegalArgumentException` (empty `NotationType`) on a fresh install; `AutoTipsManager.hideAll()` can throw `ConcurrentModificationException` (not called by the new code).
- Old `&&` comments show their `&&` in the lists (that is how they are stored).
- Previews show the PDF page plus the one comment or tag marker, not the other annotations of the copy.
- The cross-evaluation search only knows folders opened since this storage exists, plus the folders of the files list.
- Tags whose copy was renamed stay attached to the old file name (`EvaluationTags.retainCopies` exists but is not called).
- Mixed text + formula elements cannot be resized with the grab line (their width is the element max width).
- The guided tour highlights empty areas when no copy is open (first opening): it could open the demo class.
- `Compute marks` uses the marks scale of the evaluation open, for every graded copy of the files list (copies of other evaluations listed at the same time get that scale too).
- Formula errors are in English in both languages (`MarkScale.Formula` messages).
- Repo still named `nathan-ed/PDF4Teachers` on GitHub: after renaming it, change `AppLinks.REPOSITORY` and the video links of the guides' READMEs.

## Ideas for next steps

- Export of the tags (CSV per student: methods and mistakes per exercise), or show them in the Moodle feedback.
- Filter the files list by tag; tags across evaluations (same exercise in another class).
- Suggest a tag from the comments written near the same spot.
- Drag a copy from the "without method" previews onto a tag to classify it.
