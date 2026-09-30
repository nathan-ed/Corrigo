# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Video 2: grading an exercise on the ungraded copies. Usage: video2.py LANG [dry]"""
import sys
from drive import *  # S: the working folder
lang = sys.argv[1]; dry = len(sys.argv) > 2
T = {
 'en': dict(caps=['Grade one exercise on every copy', 'Point at the mistake, press #',
                  'Its points are counted, a pill shows where (never printed)', 'A comment for the student',
                  'Next copy: tag the method, give the points', 'Comments already written are suggested'],
            mistake='inner', comment='The derivative of 2x + 1 is missing', search='chain'),
 'fr': dict(caps=['Corrigez un exercice sur toutes les copies', 'Visez l’erreur, appuyez sur #',
                  'Ses points sont comptés, une pastille la montre (jamais imprimée)', 'Un commentaire pour l’élève',
                  'Copie suivante : la méthode, les points', 'Les commentaires déjà écrits sont proposés'],
            mistake='oubli', comment='Il manque la dérivée de 2x + 1', search='rapide'),
}[lang]
C = T['caps']
P = {'en': dict(chip1=(72, 314), chip2=(213, 314)), 'fr': dict(chip1=(100, 314), chip2=(100, 347))}[lang]
class NoRec:
    def __init__(self, n): pass
    def __enter__(self): return self
    def __exit__(self, *a): pass
    def caption(self, t): print('--', t)
Rec = NoRec if dry else Recorder
def snap(n):
    if dry: shot(f'{S}/demo/v2-{n}.png')
move(1500, 700, 0.1)
with Rec(f'grading-{lang}') as r:
    r.caption(C[0]); wait(1.5)
    click(150, 1036); wait(2.8); snap(1)
    r.caption(C[1]); move(905, 432); wait(0.8); key('numbersign'); wait(1.0)
    type_(T['mistake'], delay=110); wait(0.9); key('Return'); wait(1.4)
    r.caption(C[2]); wait(1.2); snap(2)
    drag(560, 432, 1080, 372); wait(1.2); snap(3)
    r.caption(C[3]); click(190, 222); type_(T['comment'], delay=50); wait(1.2); snap(4)
    click(150, 1036); wait(2.8)
    r.caption(C[4]); click(*P['chip1']); wait(0.8); click(301, 191); type_('3', pause=0.3); key('Return'); wait(1.4); snap(5)
    click(150, 1036); wait(2.8)
    r.caption(C[5]); click(*P['chip2']); wait(0.8); click(190, 222); type_(T['search'], delay=110); wait(1.4); snap(6)
    key('Down'); wait(0.5); key('Return'); wait(1.2)
    click(301, 191); type_('3', pause=0.3); key('Return'); wait(2.0); snap(7)
