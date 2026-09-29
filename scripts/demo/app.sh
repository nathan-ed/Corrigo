#!/bin/bash
# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
# app.sh start|stop LANG : the demo instance on the Full HD screen :78, with its own profile
S=${CORRIGO_DEMO_WORK:-$HOME/.cache/corrigo-demo/work}
REPO=$(cd "$(dirname "$0")/../.." && pwd)
LANG_CODE=${2:-en}
X=$S/demo/xdg-$LANG_CODE
stop(){
  for p in $(pgrep -f java); do grep -qa "XDG_DATA_HOME=$S/demo/xdg-" /proc/$p/environ 2>/dev/null && kill $p; done; sleep 2
  # The services of its private session bus (file chooser helpers...)
  for p in $(pgrep -u $(id -u)); do grep -qa "HOME=$S/demo/[a-z]*/teacher" /proc/$p/environ 2>/dev/null && kill $p 2>/dev/null; done; sleep 1
}
if [ "$1" = stop ]; then stop; exit; fi
stop
mkdir -p $X
if [ -f $X/Corrigo/settings.yml ]; then
  python3 - $X $LANG_CODE <<'PY'
import yaml,sys
X,lang=sys.argv[1],sys.argv[2]
p=X+'/Corrigo/settings.yml'; s=open(p).read()
import re
s=re.sub(r'^language: .*$', 'language: '+('fr_fr' if lang=='fr' else 'en_us'), s, flags=re.M)
s=s.replace('allowAutoTips: true','allowAutoTips: false').replace('systemTheme: true','systemTheme: false').replace('darkTheme: false','darkTheme: true').replace('storeEditionsNextToPdf: false','storeEditionsNextToPdf: true')
open(p,'w').write(s)
p=X+'/Corrigo/sync_userdata.yml'
try:
    d=yaml.safe_load(open(p)) or {}
    d['mainWindowSize']={'width':1920,'height':1080,'x':0,'y':0,'fullScreen':False}
    d.setdefault('barsSizes',{})['leftBar']=380.0
    yaml.safe_dump(d,open(p,'w'),allow_unicode=True)
except FileNotFoundError: pass
PY
fi
cd "$REPO"
(env -u SESSION_MANAGER -u DBUS_SESSION_BUS_ADDRESS DISPLAY=:78 XCURSOR_THEME=breeze_cursors XCURSOR_SIZE=36 XDG_DATA_HOME=$X HOME=$S/demo/$LANG_CODE/teacher XDG_CONFIG_HOME=$S/demo/$LANG_CODE/teacher/.config GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.gradle} JAVA_TOOL_OPTIONS=-Duser.home=$S/demo/$LANG_CODE/teacher nohup dbus-run-session -- ./gradlew --no-daemon --init-script $S/demo/demo-build.gradle run > $S/demo/run-$LANG_CODE.log 2>&1 &)
export DISPLAY=:78
for i in $(seq 1 90); do xdotool search --name "Corrigo" >/dev/null 2>&1 && break; sleep 1; done
sleep 3
for w in $(xdotool search --name "Corrigo"); do xdotool windowmove $w 0 0; xdotool windowsize $w 1920 1080; done
sleep 1
