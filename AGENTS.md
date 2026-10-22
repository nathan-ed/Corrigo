# Corrigo (fork de PDF4Teachers)

Notes de développement : `DEV_NOTES.md`.

## Règle des points : correction par lignes, décidée par Nathan le 2026-10-22, ne pas la changer

Un seul champ texte par exercice ; une ligne vide sépare les sous-questions (1er bloc = a, 2e = b… ; « b) » en tête
de bloc choisit la sous-question). Une ligne = un texte et ses points à la fin (« Notation - 0.5 », « Calculs
cohérents 1 », « −0,5 ») ; « Correct », « Cohérent », « Juste » sans nombre : tous les points ; « Faux », « Pas de
réponse », « Incomplet »… sans nombre : départ de 0 ; « PNF » (« PNF : commentaire ») : une marque de présentation ; ce
qui est entre parenthèses n'est jamais écrit sur la copie. Le mode est **par sous-question** : un mot « tous les
points » ou « départ de 0 » décide, sinon la première ligne à points (une erreur : on part du maximum, une méthode : de
0). Note = départ + points, entre 0 et le maximum ; une sous-question sans ligne n'est pas notée.

« g » après les points (« Notation -0.5 g ») : la ligne est proposée dans tous les exercices du test (2026-10-22).
En tapant, les lignes déjà utilisées qui correspondent sont proposées sous le champ (↑ ↓, Tab) : pas de pastilles.

Toujours : ouvrir une copie ne change jamais une note ; une note change seulement quand on modifie le texte de son
exercice ; une note tapée à la main reste (✎) jusqu'au clic sur ↺, même si les lignes changent ; une copie notée avec
une version précédente garde ses notes. Code : `LineGrading.grade`, `LineCorrection.applyGrades`.

L'ancien mode (pastilles méthodes/erreurs, mode par exercice) reste quand le réglage « Correction par lignes » est
désactivé : règle du 2026-10-18 (`ScoredCommentGrades.computeExercise`).

## Données des élèves : règle absolue

JAMAIS les vraies données. Ne jamais lire, lister, copier, analyser ni ouvrir dans l'application les
vraies évaluations (`~/Desktop/Evaluations/`, copies réelles, `.pdf4teachers/`) ni les données réelles
de l'application (`~/.local/share/Corrigo/`, `~/.local/share/PDF4Teachers/`), même en lecture seule, même
copiées dans `/tmp`. Tout test se fait en sandbox, toujours et uniquement sur la classe fictive
ALPHA, BRAVO, CHARLIE… (`scripts/make_test_eval.py`). On ne cherche jamais rien d'autre (aucune
recherche de copies, d'évaluations ou de PDF dans le dossier personnel). Skill `corrigo-test`
(`.claude/skills/corrigo-test/SKILL.md`), à charger avant tout test ou lancement.
