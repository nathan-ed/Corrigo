# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Screenshots of the new evaluation flow and the scale assistant. Usage: shots1.py LANG"""
import sys
from drive import *  # S: the working folder
lang = sys.argv[1]; O = f'{S}/demo/shots/{lang}'
import os; os.makedirs(O, exist_ok=True)
W = {'en': dict(method='Method', result='Result', choose=(682, 328)), 'fr': dict(method='Méthode', result='Résultat', choose=(671, 348))}[lang]
DLG = (580, 234, 760, 612)
click(1150, 582); wait(1.3); shot(f'{O}/new-evaluation-1-scan-empty.png', DLG)
click(*W['choose']); wait(2); dclick(593, 173); wait(1.6); shot(f'{O}/new-evaluation-1-scan.png', DLG)
click(1286, 820); wait(0.8)
click(960, 500); paste('Ada\nBlaise\nCarl\nEmmy\nÉvariste\nHypatia\nIsaac\nLeonhard\nMaryam\nPierre\nSophie\nSrinivasa\n'); wait(1)
move(1500, 950); shot(f'{O}/new-evaluation-2-students.png', DLG)
click(1286, 820); wait(3); move(1500, 950); wait(0.5); shot(f'{O}/new-evaluation-3-pages.png', DLG)
click(1286, 820); wait(1); move(1500, 950); shot(f'{O}/new-evaluation-4-scale.png', DLG)
click(1286, 820); wait(5)
click(851, 370); click(851, 370, pause=0.6)
click(920, 376); key('ctrl+a'); type_(W['method']); click(995, 376); type_('2')
click(1075, 376); key('ctrl+a'); type_(W['result']); click(1150, 376); type_('1')
click(851, 404); click(851, 404, pause=0.6)
click(995, 410); type_('2'); click(1150, 410); type_('2'); click(903, 444); type_('3'); move(1500, 950); wait(0.6)
shot(f'{O}/scale-assistant.png', (580, 240, 760, 600))
click(1286, 814); wait(4)
shot(f'{O}/after-creation.png')
