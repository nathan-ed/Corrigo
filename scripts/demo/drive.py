# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Drives the demo app on the Full HD screen :78: smooth mouse, human typing, screenshots, recording with captions."""
import os, subprocess, time, math, json

S = os.environ.get('CORRIGO_DEMO_WORK', os.path.expanduser('~/.cache/corrigo-demo/work'))
ENV = dict(os.environ, DISPLAY=':78')

def x(*args, out=False):
    r = subprocess.run(['xdotool', *map(str, args)], env=ENV, capture_output=True, text=True)
    return r.stdout.strip() if out else None

def pos():
    loc = dict(p.split(':') for p in x('getmouselocation', out=True).split()[:2])
    return int(loc['x']), int(loc['y'])

def move(tx, ty, dur=None):
    fx, fy = pos()
    dist = math.hypot(tx - fx, ty - fy)
    if dur is None: dur = min(0.75, 0.22 + dist / 2600)
    steps = max(2, int(dur * 60))
    for i in range(1, steps + 1):
        t = i / steps
        e = t * t * (3 - 2 * t)
        x('mousemove', int(fx + (tx - fx) * e), int(fy + (ty - fy) * e))
        time.sleep(dur / steps)

RECORDER = None

def _mark(tx, ty):
    if RECORDER: RECORDER.clicks.append((time.time(), tx, ty))

def click(tx, ty, button=1, pause=0.35, dur=None):
    move(tx, ty, dur); time.sleep(0.12); _mark(tx, ty); x('click', button); time.sleep(pause)

def rclick(tx, ty, pause=0.5): click(tx, ty, 3, pause)

def dclick(tx, ty, pause=0.4):
    move(tx, ty); time.sleep(0.1); _mark(tx, ty); x('click', '--repeat', 2, '--delay', 90, 1); time.sleep(pause)

def type_(text, delay=55, pause=0.3):
    x('type', '--delay', delay, '--', text); time.sleep(pause)

def key(*keys, pause=0.3):
    x('key', *keys); time.sleep(pause)

def wait(s): time.sleep(s)

def shot(path, crop=None):
    """crop: (x, y, w, h)"""
    args = ['import', '-window', 'root']
    if crop: args += ['-crop', '%dx%d+%d+%d' % (crop[2], crop[3], crop[0], crop[1]), '+repage']
    subprocess.run(args + [path], env=ENV, check=True)

def paste(text):
    p = subprocess.Popen(['xclip', '-selection', 'clipboard'], stdin=subprocess.PIPE, env=ENV)
    p.communicate(text.encode()); time.sleep(0.2); key('ctrl+v')

def windows(geometry=None):
    ids = x('search', '--onlyvisible', '--name', '.*', out=True).split()
    res = []
    for w in ids:
        g = x('getwindowgeometry', w, out=True)
        if geometry is None or geometry in g: res.append(w)
    return res

class Recorder:
    def __init__(self, name):
        self.name = name; self.captions = []; self.clicks = []; self.proc = None
    def __enter__(self):
        self.raw = f'{S}/demo/rec/{self.name}.raw.mp4'
        os.makedirs(f'{S}/demo/rec', exist_ok=True)
        self.proc = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'x11grab', '-draw_mouse', '1', '-framerate', '30',
                                      '-video_size', '1920x1080', '-i', ':78', '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '16',
                                      '-pix_fmt', 'yuv420p', self.raw], stdin=subprocess.PIPE, env=ENV)
        self.tp = time.time()
        time.sleep(1.2); self.t0 = time.time(); self.current = None
        global RECORDER; RECORDER = self
        return self
    def caption(self, text):
        now = time.time()
        if self.current: self.captions.append((self.current[0], now, self.current[1]))
        self.current = (now, text) if text else None
    def __exit__(self, *exc):
        self.caption(None)
        time.sleep(0.8)
        global RECORDER; RECORDER = None
        ts = time.time()
        self.proc.stdin.write(b'q'); self.proc.stdin.flush(); self.proc.wait()
        json.dump(dict(popen=self.tp, start=self.t0, stop=ts, captions=self.captions, clicks=self.clicks),
                  open(f'{S}/demo/rec/{self.name}.json', 'w'), ensure_ascii=False)

def drag(fx, fy, tx, ty, dur=0.6):
    move(fx, fy); time.sleep(0.15); _mark(fx, fy); x('mousedown', 1); time.sleep(0.15)
    move(tx, ty, dur); time.sleep(0.15); x('mouseup', 1); time.sleep(0.3)

def raise_window(name_part):
    """Brings a window to the front (there is no window manager to keep the dialogs above the main window)."""
    for w in windows():
        if name_part in (x('getwindowname', w, out=True) or ''):
            x('windowraise', w); x('windowfocus', w); time.sleep(0.3)
            return True
    return False
