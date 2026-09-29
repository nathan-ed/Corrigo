# Grade scale and grading

## The grade scale (/20 tab)

The grade scale is a tree: the total, the exercises, and their sub-questions, each with its points. The exercises are
the first level under the total (the app calls them *exercises* everywhere: exercise mode, grading panel, comments,
methods and mistakes).

- **Build it**: the **+** of a grade adds a sub-grade under it; `Ctrl+G` adds a grade at the same level as the
  selected one. `Tab` and `Enter` move between the name and the points fields.
- **Enter the points** in the grade's field, or right-click it: **Set to 0**, **Set maximum mark**, **Reset**. A
  double-click sets it to 0. `Ctrl+N` selects the next grade to enter.
- The parents are summed automatically. A grade named `Bonus…` is not counted in the maximum, only in the result.
- **Scale to**: express the total on another scale (e.g. /20).
- **Place the grades on the page**: each grade is written where you put it on the copy (drag it). Right-click → hide
  the unfilled grades.
- **Lock** (padlock icon): prevents changing the scale by mistake while grading.
- **Fonts and colors** (gear icon): font, color, prefix of each level, show the name, hide a grade, hide it when full.
- **Copy the scale to the other copies** (link icon): to all the listed copies or to those of the same folder. Keep
  **Copy marks position** checked so that the grades are at the same place on every copy.
- **Export / import a grade scale** as a file: **Tools → Export/Import edits or marking scales**.

A typical Swiss scale starts with a "PNF" exercise (presentation), then Q1…Qn.

## Exercise mode (footer bar)

Correct exercise by exercise, not copy by copy:

1. Turn on **Exercise** in the footer bar and choose the exercise (`Alt+↑` / `Alt+↓`).
2. **Pages**: set the page where each exercise is on the copies. The grading panel and the page buttons use it.
3. Switching copy then opens the next copy at the page of the selected exercise:
   `Ctrl+Alt+Shift+←/→` or `Alt+Page Up/Down` keep the page (or go to the exercise page),
   `Alt+1…9` jumps to the page of exercise 1…9.

## The grading panel (list icon)

The panel shows the exercise being graded, its sub-grades, and everything to grade it quickly. `Ctrl+Shift+G` focuses
it from anywhere.

**Header**: the exercise and its points, `‹ ›` for the previous/next exercise, the copy (e.g. "07_DUPONT.pdf · copy
7/24") and a button per exercise to jump to its page (✓ when graded on this copy). The panel follows the page you are
reading.

**One section per sub-grade**, the active one highlighted:

- the points field and the maximum; "from max" / "from 0" says whether the scored comments count down from the maximum
  or up from zero (click to switch);
- the **scored comments** of this sub-grade (see below): click one to put it on the copy or remove it;
- **+ Add a scored comment (N)**: type the points (e.g. `-1`) and the text, `Enter`;
- **Comment on the copy (C)**: the comment of this sub-grade, written next to its grade while you type.
  Emptying the field removes it.

**General comment (G)**: the comment for the whole exercise.

**Methods & mistakes**: see [Methods and mistakes](04-methods-and-mistakes.md).

**Footer**: **‹ Previous** / **Next ungraded ›** open the previous/next copy where this exercise is not fully graded;
**Mark position** / **Compute marks** (see [Marks](#marks)).

### Keyboard in the panel

When the panel has the keyboard (click its empty space, or `Ctrl+Shift+G`):

| Key | Action |
|---|---|
| `1`…`9` | put (or remove) the n-th scored comment of the active sub-grade |
| `0` | 0 points for the active sub-grade |
| `=` or `+` | full points |
| `↑` `↓`, `Enter` / `Shift+Enter` | previous / next sub-grade |
| `←` `→` | previous / next copy |
| `Tab` | the points field of the active sub-grade (`Shift+Tab`: its comment) |
| `C` | the comment field of the active sub-grade |
| `N` | new scored comment for the active sub-grade |
| `G` | the general comment |
| `Z` / `Shift+Z` | next / previous ungraded copy |
| `Delete` | remove the comment selected on the copy (e.g. the one just placed) |
| `Esc` | give the keyboard back to the document |

In the fields:

- **Tab / Shift+Tab** go through the whole exercise: points → comment → points of the next sub-grade → … → general
  comment, and back. The points are applied when you leave the field.
- **Enter** in a points field applies it and goes to the next sub-grade. In a comment field it writes the comment and
  goes to the next sub-grade (from the general comment: to the next ungraded copy). **Ctrl+Enter** writes the comment
  where you click on the page instead of next to the grade.
- **Esc** cancels the edit of the field.

### Comment suggestions

When a comment field gets the focus, or while you type, the comments of the evaluation are suggested under it:

1. first those already written **in this field** (this sub-grade) on other copies,
2. then those of **this exercise**,
3. then all the others (their exercise is shown on the right, with the number of copies using them).

Typing filters them (every word, accents and case ignored). `↓` selects one, `Enter` or a click writes it on the copy.
The suggestions come from the comments written on the copies of the folder, whether from the panel or as free texts.

## Scored comments

A scored comment is a comment that adds or removes points, e.g. "Sign error −1" or "Nice method +0.5". It belongs to
a sub-grade. When you put it on a copy, the grade of that sub-grade is computed from its maximum (or from 0) and the
scored comments on the copy; a value typed by hand wins over them.

- Create them in the grading panel (**+ Add a scored comment**), in the scored comments list of the Grades tab (**+**),
  or from an existing text (right-click → **Make it a scored comment**).
- Put them on the copy from the grading panel (click, or `1`…`9`), or from the list of the Grades tab: click it, then
  click on the page (`Shift+click` to place it several times, `Esc` to stop); `1`…`9` place the listed comments at
  the mouse.
- Right-click a scored comment on a copy: change its points **for this copy only**, reset it, save this version as a
  new comment, or use this version for all the copies (the other copies are listed before being changed).
- Changing or deleting a scored comment offers to update the copies that use it.
- **Find in all copies** (right-click in the list): which copies have a given scored comment.
- Right-click a grade → **Compute from scored comments** replaces a typed value by the computed one.

The scored comments are kept per evaluation (grade scale), in the evaluation folder.

## Marks

Marks are on the Swiss 1–6 scale: **obtained / total × 5 + 1, rounded to the nearest half**.

1. **Mark position**: click it, then click on the page where the mark goes. The position is used for every copy.
2. **Compute marks**: writes the mark on every fully graded copy of the files list. If no position was set, it asks
   for one first.

The result window lists the marks, the copies that are not fully graded, and the copies for which half a point or one
point more would change the mark (double-click to open one). The marks are also available as a column in the
[spreadsheet export](06-export.md#marks-to-a-spreadsheet).
