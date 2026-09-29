# Copyright (c) 2026 Nathan
# Licensed under the Apache License, Version 2.0: see the LICENSE file.
# Part of Corrigo, a modified version of PDF4Teachers (https://github.com/ClementGre/PDF4Teachers).
"""Video 1: a new evaluation from the scan of the class."""
import sys
from drive import *  # S: the working folder
lang = sys.argv[1]
W = {'en': dict(method='Method', result='Result'), 'fr': dict(method='Méthode', result='Résultat')}[lang]
C = {
 'en': ['Start from the scan of the whole class', 'Choose the scanned PDF', 'Paste the class list, in the order of the scan',
        'Check that each copy is the right student', 'Grade scale: exercises, pages, criteria or sub-questions, points',
        'One PDF per student, ready to grade'],
 'fr': ['Partez du scan de toute la classe', 'Choisissez le PDF scanné', 'Collez la liste de la classe, dans l’ordre du scan',
        'Vérifiez que chaque copie est le bon élève', 'Le barème : exercices, pages, critères ou sous-questions, points',
        'Un PDF par élève, prêt à corriger'],
}[lang]
names = 'Ada\nBlaise\nCarl\nEmmy\nÉvariste\nHypatia\nIsaac\nLeonhard\nMaryam\nPierre\nSophie\nSrinivasa\n'
move(1500, 700, 0.1)
with Recorder(f'new-evaluation-{lang}') as r:
    r.caption(C[0]); wait(1.2)
    click(1150, 582); wait(1.3)
    r.caption(C[1]); click(*{'en': (682, 328), 'fr': (671, 348)}[lang]); wait(1.6)
    dclick(593, 173); wait(1.6)
    click(1286, 820); wait(0.8)
    r.caption(C[2]); click(960, 500); wait(0.4); paste(names); wait(2.0)
    click(1286, 820); wait(1.5)
    r.caption(C[3]); move(930, 600); wait(1.2)
    for _ in range(6): x('click', 5); time.sleep(0.25)
    wait(1.5)
    r.caption(C[4])
    click(1286, 820); wait(1.2)
    click(1286, 820); wait(3.5)
    # Ex 1: two criteria of its own
    click(851, 370); click(851, 370, pause=0.6)
    click(920, 376); key('ctrl+a'); type_(W['method'], pause=0.2); click(995, 376); type_('2', pause=0.2)
    click(1075, 376); key('ctrl+a'); type_(W['result'], pause=0.2); click(1150, 376); type_('1', pause=0.4)
    # Ex 2: sub-questions a and b
    click(851, 404); click(851, 404, pause=0.6)
    click(995, 410); type_('2', pause=0.2); click(1150, 410); type_('2', pause=0.3)
    click(903, 444); type_('3', pause=1.2)
    click(1286, 814); wait(2.5)
    r.caption(C[5]); wait(1.0)
    click(30, 46); wait(2.2)
    click(80, 46); wait(2.0)
