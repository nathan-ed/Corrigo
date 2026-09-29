# Demo videos and screenshots

The scripts that recorded `docs/videos/` and took `docs/images/`. Linux only (Xvfb, xdotool, ffmpeg). Everything
runs on a separate virtual screen (`:78`, 1920×1080) with its own profile and a fake home: your desktop, your
settings and your evaluations are never used. The class is fictional (students named after mathematicians).

## Needs

`Xvfb`, `xdotool`, `xclip`, `ffmpeg` (with libx264 and libwebp), ImageMagick (`import`, `magick`), `pngquant`,
`pdftoppm` and `img2pdf`, Python 3 with `reportlab`, `Pillow` and `PyYAML`, the `breeze_cursors` cursor theme (or
change `XCURSOR_THEME` in `app.sh`), and JDK 21. The handwriting font (Kalam, OFL) is downloaded into
`~/.cache/corrigo-demo` by `scripts/make_demo_scan.py`.

The working folder is `~/.cache/corrigo-demo/work`, or `$CORRIGO_DEMO_WORK`.

## Steps

Run everything from this folder (`cd scripts/demo`).

```
./setup.sh                     # virtual screen, fake homes, scan of a class, graded class, profiles

# Video 1: a new evaluation from the scan (starts from the empty app)
python3 reset.py en && ./app.sh start en
python3 video1.py en && python3 post.py new-evaluation-en out

# Video 2: grading; video 3: the class (start from the graded class, first copy, grading tab)
./app.sh stop; ./restore.sh en 0 && ./app.sh start en
python3 -c "from drive import *; click(80, 46); wait(1.5); click(40, 140); wait(2)"
python3 video2.py en && python3 post.py grading-en out
# (restore again before video 3, the same way)
python3 video3.py en && python3 post.py class-en out

./app.sh stop
```

Replace `en` by `fr` for the French versions. `post.py NAME OUT_DIR` writes `NAME.mp4` (captions, a ring on each
click) and an animated `NAME.webp` preview into `OUT_DIR` (a folder relative to where you run it; the working
folder's `demo/out` in the examples of the session). Copy them into `docs/videos/`.

Screenshots: `shots1.py LANG` from the empty app (after `reset.py`), `shots2.py LANG` then `shots3.py LANG` from the
graded class (after `restore.sh`). They are written in the working folder's `demo/shots/LANG/`; compress them with
`pngquant` into `docs/images/LANG/`, then rebuild the guides (`python3 scripts/build_user_guide.py`).

Each video script accepts a second argument (`video2.py en dry`) to run without recording, with screenshots of the
key moments.

## When the app changes

The scripts click at fixed positions of the 1920×1080 window: after a change of layout, check the positions with a
dry run and `drive.shot(...)`. Some differ by language (French texts wrap differently): they are in the `P` or `W`
tables of each script. Dialogs can open behind the main window (there is no window manager): `raise_window(...)`
brings them to the front.

Never start a window manager or other desktop program on `:78`: `app.sh` removes the link to your desktop session
(`SESSION_MANAGER`, the session bus) so that nothing done there reaches your desktop.
