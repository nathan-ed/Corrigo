# SANDBOX: minimal xdotool stand-in on the display of $DISPLAY (default :77, XTEST).
# xdo.py click X Y | rclick X Y | dclick X Y | drag X1 Y1 X2 Y2 | key ctrl+s | type "TEXT" (\n = Return) | move X Y | titles
import os, sys, time
from Xlib import display, X, XK
from Xlib.ext import xtest
d = display.Display(os.environ.get("DISPLAY", ":77"))
NAMES = {"é": "eacute", "è": "egrave", "à": "agrave", "ç": "ccedilla", "ê": "ecircumflex", "ù": "ugrave","ctrl": "Control_L", "shift": "Shift_L", "alt": "Alt_L", "\n": "Return", " ": "space", "-": "minus", ".": "period",
         ",": "comma", ":": "colon", "(": "parenleft", ")": "parenright", "+": "plus", "'": "apostrophe", "/": "slash", "=": "equal"}
def code(name):
    return d.keysym_to_keycode(XK.string_to_keysym(NAMES.get(name, name)))
def press(codes):
    for c in codes: xtest.fake_input(d, X.KeyPress, c); d.sync()
    for c in reversed(codes): xtest.fake_input(d, X.KeyRelease, c); d.sync()
    time.sleep(.03)
def key(combo):
    press([code(k) for k in combo.split("+")])
def spare_keycode():
    # A key with no symbol: an accented letter (é…) missing from the keyboard map is put there for the time of a press
    first, last = d.display.info.min_keycode, d.display.info.max_keycode
    for kc in range(last, first - 1, -1):
        if d.keycode_to_keysym(kc, 0) == 0: return kc
    return last
def typechar(ch):
    keysym = XK.string_to_keysym(NAMES.get(ch, ch))
    kc = d.keysym_to_keycode(keysym)
    if kc == 0:
        kc = spare_keycode()
        d.change_keyboard_mapping(kc, [(keysym, keysym)])
        d.sync()
        time.sleep(.05)
        press([kc])
        return
    shift = ch.isupper() or ch in "+:()"
    # The keyboard map decides: use the shifted level if the keysym is there
    if d.keycode_to_keysym(kc, 0) != keysym and d.keycode_to_keysym(kc, 1) == keysym: shift = True
    elif d.keycode_to_keysym(kc, 0) == keysym: shift = False
    press(([code("shift")] if shift else []) + [kc])
cmd = sys.argv[1]
if cmd == "drag":
    x1, y1, x2, y2 = map(int, sys.argv[2:6])
    xtest.fake_input(d, X.MotionNotify, x=x1, y=y1); d.sync(); time.sleep(.25)
    xtest.fake_input(d, X.ButtonPress, 1); d.sync(); time.sleep(.15)
    for k in range(1, 11):
        xtest.fake_input(d, X.MotionNotify, x=x1 + (x2 - x1) * k // 10, y=y1 + (y2 - y1) * k // 10); d.sync(); time.sleep(.04)
    time.sleep(.15); xtest.fake_input(d, X.ButtonRelease, 1); d.sync()
elif cmd == "dclick":
    xtest.fake_input(d, X.MotionNotify, x=int(sys.argv[2]), y=int(sys.argv[3])); d.sync(); time.sleep(.25)
    for _ in range(2):
        xtest.fake_input(d, X.ButtonPress, 1); d.sync(); time.sleep(.04); xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(.06)
elif cmd in ("click", "rclick", "move"):
    xtest.fake_input(d, X.MotionNotify, x=int(sys.argv[2]), y=int(sys.argv[3])); d.sync(); time.sleep(.25)
    if cmd != "move":
        b = 1 if cmd == "click" else 3
        xtest.fake_input(d, X.ButtonPress, b); d.sync(); time.sleep(.06); xtest.fake_input(d, X.ButtonRelease, b); d.sync()
elif cmd == "key":
    key(sys.argv[2])
elif cmd == "titles":  # The names of the windows (the open copy is in the title of the main window)
    net_name, utf8 = d.intern_atom("_NET_WM_NAME"), d.intern_atom("UTF8_STRING")
    def walk(w):
        try:
            prop = w.get_full_property(net_name, utf8)
            name = prop.value.decode("utf-8", "replace") if prop else w.get_wm_name()
            if name: print(name)
            for child in w.query_tree().children: walk(child)
        except Exception:
            pass
    walk(d.screen().root)
elif cmd == "type":
    for ch in sys.argv[2].replace("\\n", "\n"): typechar(ch)
