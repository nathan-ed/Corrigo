# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Screenshots of grading, methods and mistakes, class overview, previews, review, notes. Usage: shots2.py LANG"""
import sys
from drive import *  # S: the working folder
lang = sys.argv[1]; O = f'{S}/demo/shots/{lang}'
T = {'en': dict(mistake='inner', comment='The derivative of 2x + 1 is missing', chip1=(72, 314), chip2=(213, 314), search='chain',
                overview=(83, 583), badge=(239, 436), mname='Inner derivative', without=(88, 553)),
     'fr': dict(mistake='oubli', comment='Il manque la dérivée de 2x + 1', chip1=(100, 314), chip2=(100, 347), search='rapide',
                overview=(92, 649), badge=(241, 502), mname='facteur', without=(82, 619))}[lang]
click(80, 46); wait(1.5); click(40, 140); wait(2)
# The class overview (copy 1)
click(*T['overview']); wait(1.5); shot(f'{O}/class-overview.png', (0, 60, 380, 800)); click(*T['overview']); wait(1)
# Pierre: # picker, the mistake, a comment
click(150, 1036); wait(3)
move(905, 432); wait(0.3); key('numbersign'); wait(1); type_(T['mistake'], delay=60); wait(0.8)
shot(f'{O}/tag-picker.png', (600, 300, 900, 480)); key('Return'); wait(1.5)
drag(560, 432, 1080, 372); wait(1)
click(190, 222); type_(T['comment'], delay=60); wait(1.5); key('Escape'); wait(0.3); key('Escape'); wait(0.5)
move(1300, 900); wait(0.5); shot(f'{O}/grading.png')
# Srinivasa: suggestions
click(150, 1036); wait(3); click(150, 1036); wait(3)
click(*T['chip2']); wait(0.8); click(190, 222); key('ctrl+a'); type_(T['search'], delay=80); wait(1.3)
shot(f'{O}/comment-suggestions.png', (0, 150, 560, 260)); key('Escape'); key('Escape'); wait(0.5)
# Right-click a chip: its menu
rclick(120, 436 if lang == 'en' else 502); wait(1.2); shot(f'{O}/x-chip-menu.png')
key('Escape'); wait(0.5)
# Previews of a mistake, and its menu
click(150, 90) if False else None
