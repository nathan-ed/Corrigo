# Settings, data and troubleshooting

## Settings (menu bar → Settings…)

- **Accessibility**: language.
- **Usability**: dark theme or system theme, always restore the previous session, zooming/scrolling effects.
- **Save and edits**: autosave the edition, periodical autosave, **Store the edits next to the PDF files**
  (recommended: the annotations then travel with the folder).
- **Pages context menu**: what the right-click menu of a page offers.
- **Elements lists** and **Text elements**: display of the lists, maximum number of previous texts, favorites
  behaviour, default maximum width of a new text.
- **Handwriting**: when a freeform drawing is split into several elements (distance, length, duration).
- **Network**: update notifications.
- Last groups: extended tips; application zoom, PDF rendering zoom (sharper pages at the cost of memory), rendering
  fitted to the zoom, and a fix for menus that do not open on some Linux window managers.

## Where the data is

**In each evaluation folder** (next to the copies):

```
07_DUPONT.pdf              the copy (unchanged by annotations)
.07_DUPONT.pdf.yml         its annotations and grades
.pdf4teachers/
  comments.yml             the comments of the evaluation, by exercise
  scoredcomments.yml       how the points count by sub-grade ("from max" / "from 0")
  tags.yml                 methods and mistakes, their points and comments, where they are on each copy
  notes.yml, notes/        personal notes and their screenshots
```

Files starting with a dot are hidden: show hidden files in your file manager to see them. Copy, move or back up the
evaluation folder as a whole, and the annotations, comments, tags and notes go with it.

**In the app's data folder** (**Tools → Debug → Open data folder**; on Linux `~/.local/share/PDF4Teachers`): settings,
the files list, text lists, favorite figures and images, the annotations of copies not stored next to their PDF, and
notes taken with no copy open.

## Troubleshooting

- **A copy lost its annotations after being renamed or moved**: rename copies from the app. Otherwise open the copy and
  use **Tools → Edits of documents with same name** to take back the annotations of the old name.
- **A copy cannot be loaded**: the PDF may be damaged or unreadable. If only its annotations are damaged, the app says
  where the edit file is; fix or delete it (**Tools → Debug → Open editings file**).
- **Wrong exercise for a comment in "This evaluation"**: right-click it → **Move to exercise**. **Read the comments of
  the copies again** from the list menu if something looks outdated.
- **Tags attached to an old file name**: tags follow the copy's file name; rename copies before tagging.
- **Something went wrong**: **Tools → Debug → Open execution console** shows the messages of the app; copy them when
  reporting a problem.
