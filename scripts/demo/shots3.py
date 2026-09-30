# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Continues shots2: points dialog, previews and their menu, review, without method, notes. Usage: shots3.py LANG"""
import sys
from drive import *  # S: the working folder
lang = sys.argv[1]; O = f'{S}/demo/shots/{lang}'
T = {'en': dict(badge=(239, 436), chipy=436, mname='Inner derivative', without=(88, 553), wname='without method'),
     'fr': dict(badge=(241, 502), chipy=502, mname='facteur', without=(82, 619), wname='sans méthode')}[lang]
def dialog_window(skip):
    for w in windows():
        n = x('getwindowname', w, out=True) or ''
        if n and skip not in n and 'Corrigo' not in n: return w
    return None
# Points and comment of a mistake
rclick(120, T['chipy']); wait(1); click(200, T['chipy'] + 25); wait(1.5)
shot(f'{O}/points-and-comment.png', (560, 250, 800, 560)); key('Escape'); wait(1)
# Previews of a mistake, and the right-click menu
click(*T['badge']); wait(2.5); raise_window(T['mname']); move(700, 600); key('ctrl+plus'); key('ctrl+plus'); key('ctrl+plus'); wait(1.5)
move(1300, 900); shot(f'{O}/previews.png', (460, 54, 1000, 980))
rclick(1178, 500); wait(1.2); shot(f'{O}/previews-menu.png', (460, 54, 1100, 1000)); key('Escape'); wait(0.5)
# Review mode
click(1296, 1000); wait(3); move(1300, 900); shot(f'{O}/review.png', (0, 0, 1920, 560)); click(355, 90); wait(1.5)
# Copies without a method, and their menu
click(*T['without']); wait(2.5); raise_window(T['wname']); wait(1)
rclick(1178, 880); wait(1.2); shot(f'{O}/without-method-menu.png', (460, 54, 1000, 1026)); key('Escape'); wait(0.5)
click(1429, 1000); wait(1)
# A note with a screenshot, from the page menu
rclick(1100, 800); wait(1.2); shot(f'{O}/page-menu.png', (900, 600, 700, 480)); key('Escape'); wait(0.5)
NOTE = {'en': 'Ask the class about the chain rule next lesson', 'fr': 'Revoir la dérivation d\'une composée au prochain cours'}[lang]
rclick(1100, 800); wait(1.2); click(1150, 908); wait(1.5); type_(NOTE, delay=20); key('Return'); wait(1)
# The notes tab
click(130, 46); wait(2.5); shot(f'{O}/notes.png', (0, 0, 380, 700))
click(80, 46); wait(1)
