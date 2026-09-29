#!/bin/bash
# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
#
# Prepares the working folder of the demo recordings (see README.md): the virtual screen :78, a fake home per
# language with the scan of a class, a graded class, and the app profiles. Nothing outside the working folder is
# touched, except starting Xvfb :78.
set -e
S=${CORRIGO_DEMO_WORK:-$HOME/.cache/corrigo-demo/work}
REPO=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$S/demo/rec" "$S/demo/out" "$S/demo/shots"

# The Full HD virtual screen of the recordings
if ! DISPLAY=:78 xdpyinfo >/dev/null 2>&1; then
  (nohup Xvfb :78 -screen 0 1920x1080x24 >/dev/null 2>&1 &)
  sleep 2
fi

# Its own build folder: the build of the app you use is not rewritten. The app runs from the fake home (the file
# chooser shows its folder).
cat > "$S/demo/demo-build.gradle" <<EOF
allprojects {
    layout.buildDirectory.set(file("$S/build"))
    tasks.matching { it.name == 'run' }.configureEach { workingDir = file(System.getenv('HOME')) }
}
EOF

for L in en fr; do
  HOME_DIR="$S/demo/$L/teacher"
  mkdir -p "$HOME_DIR/Documents/Evaluations/2025-2026/2M" "$HOME_DIR/Documents/Evaluations/2025-2026/3M" \
           "$HOME_DIR/Desktop" "$HOME_DIR/.config/gtk-3.0"
  : > "$HOME_DIR/.config/gtk-3.0/bookmarks"
  # The scan of the whole class (video 1), and a class partly graded (videos 2 and 3, screenshots)
  python3 "$REPO/scripts/make_demo_scan.py" scan "$HOME_DIR/Documents/Evaluations/2025-2026/2M/Test 3.pdf" --lang $L
  rm -rf "$S/demo/test2-$L.pristine"
  python3 "$REPO/scripts/make_demo_scan.py" graded "$S/demo/test2-$L.pristine" --lang $L --graded 9

  # The app profile: language, dark theme, no tips, annotations next to the PDFs (no welcome window)
  X="$S/demo/xdg-$L/Corrigo"
  mkdir -p "$X"
  if [ ! -f "$X/settings.yml" ]; then
    cat > "$X/settings.yml" <<EOF
language: $([ $L = fr ] && echo fr_fr || echo en_us)
systemTheme: false
darkTheme: true
allowAutoTips: false
storeEditionsNextToPdf: true
autoSave: true
animations: true
renderWithZoom: true
restoreLastSession: true
textOnlyStart: true
listsMoveAndDontCopy: true
checkUpdates: false
sendStatistics: false
EOF
  # (A missing on/off setting is read as off: the defaults the videos need are written above.)
  fi
  # The window fills the virtual screen, the side bar as wide as in the videos
  if [ ! -f "$X/sync_userdata.yml" ]; then
    printf 'mainWindowSize: {width: 1920, height: 1080, x: 0, y: 0, fullScreen: false}\nbarsSizes: {leftBar: 380.0, rightBar: 270.0}\n' > "$X/sync_userdata.yml"
  fi
done
echo "Ready in $S"
