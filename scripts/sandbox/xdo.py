# SANDBOX: minimal xdotool stand-in on display :77 (XTEST).
# xdo.py click X Y | rclick X Y | key ctrl+s | type "TEXT" (\n = Return) | move X Y
import sys, time
from Xlib import display, X, XK
from Xlib.ext import xtest
d = display.Display(":77")
NAMES = {"ctrl": "Control_L", "shift": "Shift_L", "alt": "Alt_L", "\n": "Return", " ": "space", "-": "minus", ".": "period",
         ",": "comma", ":": "colon", "(": "parenleft", ")": "parenright", "+": "plus", "'": "apostrophe", "/": "slash", "=": "equal"}
def code(name):
    return d.keysym_to_keycode(XK.string_to_keysym(NAMES.get(name, name)))
def press(codes):
    for c in codes: xtest.fake_input(d, X.KeyPress, c); d.sync()
    for c in reversed(codes): xtest.fake_input(d, X.KeyRelease, c); d.sync()
    time.sleep(.03)
def key(combo):
    press([code(k) for k in combo.split("+")])
def typechar(ch):
    keysym = XK.string_to_keysym(NAMES.get(ch, ch))
    kc = d.keysym_to_keycode(keysym)
    shift = ch.isupper() or ch in "+:()"
    # The keyboard map decides: use the shifted level if the keysym is there
    if d.keycode_to_keysym(kc, 0) != keysym and d.keycode_to_keysym(kc, 1) == keysym: shift = True
    elif d.keycode_to_keysym(kc, 0) == keysym: shift = False
    press(([code("shift")] if shift else []) + [kc])
cmd = sys.argv[1]
if cmd in ("click", "rclick", "move"):
    xtest.fake_input(d, X.MotionNotify, x=int(sys.argv[2]), y=int(sys.argv[3])); d.sync(); time.sleep(.25)
    if cmd != "move":
        b = 1 if cmd == "click" else 3
        xtest.fake_input(d, X.ButtonPress, b); d.sync(); time.sleep(.06); xtest.fake_input(d, X.ButtonRelease, b); d.sync()
elif cmd == "key":
    key(sys.argv[2])
elif cmd == "type":
    for ch in sys.argv[2].replace("\\n", "\n"): typechar(ch)
