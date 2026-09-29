# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Video 3: the class overview, the copies of a mistake, a note with a screenshot, review mode, copies without a method, notes."""
import sys
from drive import *  # S: the working folder
lang = sys.argv[1]; dry = len(sys.argv) > 2
C = {
 'en': ['The class at a glance: methods, mistakes, average points', 'Every copy with a mistake, side by side',
        'A personal note with a screenshot, never on the copy', 'Review these copies one by one',
        'Copies without a method: tag them without opening them', 'All your notes, with their copy'],
 'fr': ['La classe d’un coup d’œil : méthodes, erreurs, points moyens', 'Toutes les copies avec une erreur, côte à côte',
        'Une note personnelle avec une capture, jamais sur la copie', 'Revoir ces copies une par une',
        'Copies sans méthode : indiquez-la sans les ouvrir', 'Toutes vos notes, avec leur copie'],
}[lang]
M = {'en': dict(mistake='Inner derivative', without='without method'), 'fr': dict(mistake='intérieure', without='sans méthode')}[lang]
P = {'en': dict(overview=(83, 583), bar1=660, bar2=750, whole=(152, 613), hide=(82, 616), badge=(239, 436), without=(88, 553)),
     'fr': dict(overview=(92, 649), bar1=720, bar2=810, whole=(155, 680), hide=(93, 648), badge=(241, 502), without=(82, 619))}[lang]
NOTE = {'en': 'Show this mistake in class', 'fr': 'Montrer cette erreur en classe'}[lang]
class NoRec:
    def __init__(self, n): pass
    def __enter__(self): return self
    def __exit__(self, *a): pass
    def caption(self, t): print('--', t)
Rec = NoRec if dry else Recorder
def snap(n):
    if dry: shot(f'{S}/demo/v3-{n}.png')
move(1500, 700, 0.1)
with Rec(f'class-{lang}') as r:
    r.caption(C[0]); wait(1.0)
    click(*P['overview']); wait(1.2)
    move(180, P['bar1'], 0.6); wait(0.6); move(180, P['bar2'], 0.6); wait(1.0)
    click(*P['whole']); wait(2.6); snap(1)
    click(*P['hide']); wait(1.0)                      # Hides the overview
    r.caption(C[1]); click(*P['badge']); wait(2.2); raise_window(M['mistake'])
    move(700, 600, 0.5); key('ctrl+plus'); key('ctrl+plus'); key('ctrl+plus'); wait(1.8); snap(2)
    r.caption(C[2]); rclick(706, 500); wait(1.2); snap(3)
    click(838, 1005); wait(1.6)
    click(960, 745); type_(NOTE, delay=55, pause=0.6); key('Return'); wait(1.2)
    raise_window(M['mistake']); wait(0.8)
    r.caption(C[3]); click(1296, 1000); wait(2.6)
    click(321, 90); wait(2.6); snap(4)
    click(355, 90); wait(1.2)
    r.caption(C[4]); click(*P['without']); wait(2.5); raise_window(M['without']); wait(1.2)
    rclick(1178, 880); wait(1.3); snap(5)
    click(1241, 747); wait(2.2)
    click(1429, 1000); wait(1.0)
    r.caption(C[5]); click(130, 46); wait(3.0); snap(6)
