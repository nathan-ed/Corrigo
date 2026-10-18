# Corrigo (fork de PDF4Teachers)

Notes de développement : `DEV_NOTES.md`.

## Règle des points : décidée par Nathan le 2026-10-18, ne pas la changer

Le premier point placé dans un exercice sur une copie décide comment comptent ses points :

- une erreur en premier : **par erreurs**, on part du maximum ; les erreurs enlèvent, une méthode peut rendre ;
- une méthode ou un critère en premier : **par critères**, on part de 0 ; critères cochés et méthodes ajoutent, erreurs
  enlèvent ; une sous-note sans rien vaut 0 (ce qui est affiché est ce qui compte).

Ce choix reste tant qu'un point compte dans l'exercice. Il est affiché en haut du panneau de correction et peut être
changé par exercice (toute la classe) et par copie. Toujours : un critère coché donne ses points moins les erreurs,
une note tapée à la main reste prioritaire, Ctrl+Z annule le dernier geste, une copie notée avec une version
précédente garde ses notes. Code : `ScoredCommentGrades.computeExercise`, `getChosenMode`, `getStart`.

## Données des élèves : règle absolue

JAMAIS les vraies données. Ne jamais lire, lister, copier, analyser ni ouvrir dans l'application les
vraies évaluations (`~/Desktop/Evaluations/`, copies réelles, `.pdf4teachers/`) ni les données réelles
de l'application (`~/.local/share/Corrigo/`, `~/.local/share/PDF4Teachers/`), même en lecture seule, même
copiées dans `/tmp`. Tout test se fait en sandbox, toujours et uniquement sur la classe fictive
ALPHA, BRAVO, CHARLIE… (`scripts/make_test_eval.py`). On ne cherche jamais rien d'autre (aucune
recherche de copies, d'évaluations ou de PDF dans le dossier personnel). Skill `corrigo-test`
(`.claude/skills/corrigo-test/SKILL.md`), à charger avant tout test ou lancement.
