# Vérifier les notes d'une évaluation corrigée avec Corrigo 1.6.0 ou 1.7.0

Corrigo 1.6.0 et 1.7.0 peuvent changer certaines notes **à l'ouverture d'une copie**, sans aucune action de ta part.
Ce document dit quoi vérifier, dans quel fichier, et quoi faire. Tout ce qui est décrit ici a été reproduit dans
l'application, sur des copies fictives (ALPHA, BRAVO, CHARLIE…).

## 0. Avant tout

1. **Sauvegarde** le dossier de l'évaluation tel qu'il est (PDF + fichiers cachés `.<copie>.pdf.yml` + dossier
   `.pdf4teachers/`).
2. **N'ouvre plus de copie** avec la 1.6.0 ou la 1.7.0 tant que la version corrigée n'est pas installée : chaque
   ouverture peut appliquer les cas 1 et 2 ci-dessous, et l'enregistrement automatique les écrit.

## 1. Vérification automatique (recommandé)

```
python3 scripts/verify_grades.py "<dossier de l'évaluation>"
python3 scripts/verify_grades.py "<dossier de l'évaluation>" --anonymous   # copies numérotées, rien d'identifiant
python3 scripts/verify_grades.py "<dossier>" --pnf-name "PNF"               # si l'exercice de présentation a un autre nom
```

Le script **ne modifie rien** (lecture seule). Il lit les fichiers `.<copie>.pdf.yml` (le réglage « enregistrer les
éditions à côté du PDF » doit être activé, c'est le cas par défaut dans Corrigo). Il classe ce qu'il trouve :

- **RISQUE** : la note **va changer** à la prochaine ouverture de la copie avec la 1.6.0/1.7.0. La note actuelle du
  fichier est la tienne : ne rouvre pas la copie avant la correction de Corrigo.
- **A VERIFIER** : la note **a peut-être déjà changé** à une ouverture précédente. Compare avec ce que tu voulais
  mettre.
- **INFO** : rien n'a changé, à savoir seulement.

## 2. Les règles, une par une (pour vérifier à la main ou avec une IA)

Vocabulaire du fichier `.<copie>.pdf.yml` :

- `grades` : la liste des notes. Le chemin d'une note est `parentPath` + `\` + `name` (ex. `\Total\Ex 2\a`).
  `value: -1` = vide. Une **sous-note** est une note qui n'a pas d'enfant (aucune autre note n'a son chemin comme
  `parentPath`). Un **exercice** est un enfant direct du total (`parentPath: \Total`).
- `texts` → `pageN` : les textes de la copie. Une **pastille** (méthode, erreur, critère) est un texte qui a
  `scoredCommentId` : ses points sont `points`, sa sous-note est `gradePath`. Une pastille avec `noPoints: true` ne
  compte pas. Une pastille de méthode ou d'erreur du panneau a un `scoredCommentId` qui commence par `tag:`. Un critère
  coché a `comment: $\Large\checkmark$`.
- Sur une sous-note : `valueSource: COMMENTS` = la note est **calculée** à partir des pastilles ;
  `valueBeforeComments` = la valeur **tapée** avant ce calcul ; `pointsStart` = départ gardé d'une ancienne version.
- Sur un exercice : `pointsAutoMode: FULL` (par erreurs) ou `ZERO` (par critères) = mode fixé par le premier point.

`Σ` = somme des `points` des pastilles de la sous-note (sans celles qui ont `noPoints: true`). « Borné » = ramené entre
0 et le `total` de la sous-note.

### Règle 1 : note tapée à la main sur une sous-note qui a des pastilles (RISQUE)

**Si** une sous-note a au moins une pastille `tag:…` qui compte, **et** n'a pas `valueSource: COMMENTS`, **et** a
`value` ≥ 0,
**alors** c'est une note retouchée à la main. À la prochaine ouverture, elle deviendra borné(`value` + Σ) (ou
borné(Σ) si un critère est coché) : les points des pastilles sont **retirés ou ajoutés une seconde fois**.
→ Ne pas rouvrir la copie avant la correction. La note du fichier est la bonne.

*Reproduit : a = 1 tapé à la main avec une erreur −0,5 → 0,5 après ouverture ; total 6,5 → 6.*

### Règle 1b : la même chose, déjà arrivée (A VERIFIER)

**Si** une sous-note a des pastilles `tag:…` qui comptent (Σ ≠ 0, pas de critère coché), **et** `valueSource: COMMENTS`,
**et** `valueBeforeComments` = B ≥ 0,
**alors** la note affichée vaut borné(B + Σ). Deux possibilités, que seul toi peux trancher :
- tu as tapé B **après** avoir posé les pastilles (pour corriger la note) → **bug** : la bonne note est **B** ;
- tu as tapé B **avant** de poser les pastilles (et elles partent de B) → la note affichée est juste.

Ta valeur B est toujours dans le fichier : rien n'est perdu, elle se remet à la main.

### Règle 2 : note de présentation (PNF) (RISQUE / A VERIFIER)

L'exercice de présentation est l'exercice sans sous-note qui porte le nom réglé (par défaut `PNF`). Une **marque** est
un texte (sans `scoredCommentId`) égal à `PNF` ou qui commence par `PNF : `, hors du tableau de la page 1
(page0, x ≈ 3199, y ≈ 130459).
À chaque ouverture, dès que la correction a commencé, Corrigo met sa note à min(2 − 0,5 × max(0, marques − 1), total) :
0 ou 1 marque → 2, 2 → 1,5, 3 → 1, 4 → 0,5, 5 et plus → 0.

- **Si** la note PNF est différente de cette valeur → **RISQUE** : elle sera remplacée à la prochaine ouverture.
- **Si** la note PNF est égale à cette valeur avec 0 marque → **A VERIFIER** : si tu avais tapé une autre note, elle
  a été remplacée et **n'est pas gardée** dans le fichier. À retaper.

*Reproduit : PNF tapé 1, aucune marque → 2 après ouverture ; total 9,5 → 10,5.*

### Règle 3 : copie commencée en 1.6.0, méthode posée ensuite en 1.7.0 (A VERIFIER)

**Si** l'exercice a `pointsAutoMode: FULL`, **et** une sous-note calculée (`valueSource: COMMENTS`) sans `pointsStart`
ni `valueBeforeComments` ≥ 0 n'a que des pastilles positives (méthodes), **et** sa note est le maximum alors que Σ est
plus petit,
**alors** la méthode a donné tous les points (départ du maximum). Par méthodes, la note serait borné(Σ).

### Règle 4 : départ d'une ancienne version différent de la valeur tapée (A VERIFIER)

**Si** une sous-note calculée a `pointsStart` = P et `valueBeforeComments` = B ≥ 0 avec P ≠ B,
**alors** au prochain point posé dans l'exercice, la note peut repartir de P au lieu de B. (`pointsStart` sur une note
non calculée : sans effet pour l'instant, INFO.)

### Règle 5 : « par critères » déclenché par un commentaire à 0 point (A VERIFIER)

**Si** un exercice a `pointsAutoMode: ZERO` mais aucune pastille positive ni critère coché,
**alors** un commentaire à 0 point a probablement été posé en premier, et les sous-notes vides de l'exercice ont été
mises à 0 (`valueSource: COMMENTS`, `value: 0`, aucune pastille).

### Règle 6 : sommes (A VERIFIER)

**Si** la `value` d'une note qui a des enfants n'est pas la somme des `value` ≥ 0 de ses enfants (−1 si tous vides),
**alors** le fichier est incohérent : vérifier la copie.

## 3. Ce qui est sûr (pas besoin de vérifier)

- Une note tapée sur une sous-note **sans** pastille.
- Une note calculée à partir des pastilles que tu n'as **pas** retouchée à la main.
- Une copie de la 1.6.0 qui n'a pas reçu de nouveau point en 1.7.0 : ses notes enregistrées sont gardées.
- Une copie jamais rouverte après la dernière correction : son fichier contient exactement tes notes (seuls les cas
  RISQUE changeront si tu la rouvres).

## 4. Limites

- Le script ne connaît pas l'ordre de tes gestes : pour la règle 1b, il ne peut pas savoir si tu as tapé la note avant
  ou après les pastilles. C'est à toi de dire.
- Une note PNF tapée puis écrasée (règle 2) n'est gardée nulle part : seul ton souvenir ou une autre trace (export,
  feuille) permet de la retrouver.
- Si les éditions ne sont pas enregistrées à côté des PDF (ancien réglage), les fichiers sont dans le dossier de
  données de l'application, et le script ne les trouve pas.

## 5. Pour une IA

Donner ce document et **seulement** la sortie de `verify_grades.py --anonymous` (ou des fichiers `.yml` dont les noms et
les commentaires ont été retirés), puis demander : « Pour chaque ligne, dis-moi la note actuelle, la note qui serait la
mienne selon chaque hypothèse, et ce que je dois retaper. » Les règles de la section 2 suffisent pour refaire la
vérification sur un fichier `.yml`.
