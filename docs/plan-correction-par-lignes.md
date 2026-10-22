# Plan : la correction par lignes (Corrigo 1.8.0)

**But** : corriger vite, de façon équitable, avec un retour pour l'élève. **Un seul champ texte par exercice** : on
tape les lignes de la sous-question a, une ligne vide, celles de b, etc. Ctrl+Entrée → copie suivante. Rien d'autre à
décider pendant la correction.
**Exigence n°1 : fiabilité.** Une note ne change **jamais** sans un geste du prof sur cette note.

Décisions de Nathan (22 octobre 2026) : un champ par exercice, une ligne vide sépare les sous-questions ; mode par
sous-question ; « Correct » et « Cohérent » ne diffèrent que par le texte ; une sous-question sans ligne n'est pas
notée (vide) ; tailles réglables ; on retire les pastilles, le mode par exercice, les critères, la galerie… pour ne
garder que la correction par lignes (avec les coches et le placement au clic).

---

## 1. Ce que voit le prof

```
Ex 2 · copie 3/24 (CHARLIE)                                   2 / 4
┌ Déjà utilisé dans cet exercice ─────────────────────────────────┐
│ a) Correct (11) · Notation −0,5 (9) · Pas de réponse (2)        │
│ b) Faux (6) · Calculs cohérents +1 (12) · Erreur −0,5 (5) · PNF │
└─────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────┬──────────────────────┐
│ Correct                                  │ a) ✓ tous les points │
│ Notation - 0.5                           │    −0,5    → 1,5 / 2 │
│                                          │                      │
│ Faux                                     │ b) départ 0          │
│ Calculs cohérents 1                      │    +1                │
│ PNF                                      │    marque PNF        │
│ Erreur - 0.5                             │    −0,5    → 0,5 / 2 │
│ err▌ « Erreur de signe −0,5 » (Tab)      │                      │
└──────────────────────────────────────────┴──────────────────────┘
```

- **Un seul champ par exercice.** Chaque **bloc** (lignes séparées par une ligne vide) est une sous-question, dans
  l'ordre du barème : 1er bloc = a, 2e = b… Pour sauter une sous-question : commencer un bloc par son nom (`b)` ou
  `b.`), le bloc va à cette sous-question ; une sous-question sans bloc n'est pas notée.
- **À droite du champ, ligne par ligne, ce que Corrigo a compris** : la sous-question du bloc, les points de chaque
  ligne, la note du bloc. On voit tout de suite si une ligne n'est pas lue comme on le voulait. Un bloc de trop (plus
  de blocs que de sous-questions) est signalé en rouge.
- **Au-dessus**, toutes les lignes déjà utilisées dans cet exercice, sur toutes les copies, par sous-question, triées
  par fréquence. Un clic ajoute la ligne à l'endroit du curseur.
- **Complétion** : `err` propose la ligne la plus utilisée **dans cette sous-question** qui contient ce mot (puis
  dans l'exercice) ; Tab l'accepte, points compris.
- **Ctrl+Entrée** → copie suivante, même exercice, curseur dans le champ. **Ctrl+Maj+Entrée** → copie précédente. La
  page de l'exercice s'affiche toute seule.

## 2. La règle de calcul (une seule, écrite une fois, testée)

**Lecture d'une ligne** (souple, comme on écrit vite) : `texte [(note privée)] [points]`.
- Les points sont le **nombre à la fin de la ligne**, avec ou sans signe, avec ou sans espace : `+1`, `1`,
  `- 0.5`, `−0,5`, `-0.5 pt`, `0,5pts`. Sans signe = méthode (+). Avec `-` ou `−` = erreur.
- Un nombre qui fait partie du texte n'est pas lu comme des points quand il suit `=`, `<`, `>`, `^`, `/`, `x` ou une
  lettre collée (`x = 3`, `2^3`, `Q2`) ; en cas de doute, ce que Corrigo a compris est affiché à droite, et un nombre
  écrit `"3"` (entre guillemets) n'est jamais des points.
- Sans nombre : commentaire seul (aucun point).
- Ce qui est entre parenthèses n'est **jamais** écrit sur la copie.
- **« Tous les points »** : une ligne **sans nombre** qui est `Correct`, `Juste` ou `Cohérent` (liste réglable,
  majuscules et accents ignorés). Écrit sur la copie : ✓ + le texte.
- **« Départ de 0 »** : une ligne **sans nombre** qui est `Faux`, `Pas de réponse`, `Incomplet`, `Insuffisant`,
  `Réponse erronée`, `Rien` (liste réglable). Des méthodes peuvent suivre : `Faux` / `Calculs cohérents 1` → 1.
- **`PNF`** (ou `PNF : commentaire`) : une marque de présentation pour cet exercice (compte dans le tableau de la
  page 1 et la note PNF, se place au clic comme les autres lignes) ; aucun point dans la sous-question.

**Note d'une sous-question** (mode décidé **par sous-question**, par son bloc) :
1. aucun bloc → pas notée (vide), ou la note tapée ;
2. une ligne « tous les points » → départ du **maximum** ; une ligne « départ de 0 » → départ de **0** ;
3. sinon la première ligne à points décide : erreur → départ du maximum (par erreurs), méthode → départ de 0 ;
4. note = départ + somme des points, ramenée entre 0 et le maximum ;
5. **une note tapée à la main gagne toujours** : elle est marquée « ✎ tapée », reste tant que le prof ne clique
   pas « ↺ revenir au calcul », et n'est jamais recalculée ni à l'ouverture, ni quand on change une ligne.

Exemple de Nathan, Ex /4 avec a /2 et b /2 :
`Correct` / `Notation - 0.5` → a = 2 − 0,5 = **1,5** ; (ligne vide) ; `Faux` / `Calculs cohérents 1` / `PNF` /
`Erreur - 0.5` → b = 0 + 1 − 0,5 = **0,5**, plus une marque PNF pour l'exercice.

**Invariants (tests automatiques)** :
- ouvrir, enregistrer, rouvrir une copie 20 fois → aucun octet de note ne change ;
- la note enregistrée = le calcul des lignes enregistrées, sauf note tapée ;
- changer une ligne du bloc de a ne change que a (et les sommes) ;
- aucune autre copie n'est modifiée sans une confirmation qui liste les copies et les notes avant/après.

## 3. Équité

- **Même ligne, mêmes points** : si une ligne existe déjà avec d'autres points (ex. « Erreur formule −1 » sur 8
  copies, « −0,5 » ici), elle est soulignée en orange avec le détail. Bouton **« Harmoniser »** : liste les copies et
  leurs notes avant/après, applique seulement après confirmation, et peut être annulé.
- **Vue d'ensemble de l'exercice** (une fenêtre) : chaque ligne, ses points, le nombre de copies, et les copies
  sans aucune ligne pour une sous-question (oubli).

## 4. Placement sur la copie

- Les lignes tapées sont d'abord **« à placer »** (📍 dans le champ).
- **Touche P** (ou bouton « Placer ») : chaque clic sur la page pose la ligne suivante, dans l'ordre. Échap la met
  dans la colonne à côté de la note de sa sous-question.
- En passant à la copie suivante, les lignes non placées vont **automatiquement** dans la colonne (réglable) :
  l'élève a toujours son retour.
- Écrit sur la copie : `Correct` / `Cohérent` → **✓ vert** + le texte ; méthode en vert, erreur en rouge, commentaire
  en bleu ; les points « (−0,5) » affichés ou non (réglage) ; jamais la note privée.
- Placement automatique **jamais sur l'écriture de l'élève** : recherche de place libre avec filtre des petites taches
  du scan (B18), pour tout ce qui est posé automatiquement.
- Chaque texte posé est lié à sa ligne (`sous-question` + `texte` + `n°`) : modifier la ligne modifie le texte ;
  supprimer la ligne supprime le texte ; supprimer le texte sur la copie ne touche **pas** aux points (la ligne
  reste, seulement « non placée »).
- Taille des textes réglable (défaut 14, B23).

## 5. Ce qui est retiré

| Retiré | Fichiers |
|---|---|
| Pastilles méthodes/erreurs, galerie, critères, marqueurs, revue | `panel/sidebar/grades/tags/*` sauf `FreeSpot` |
| Commentaires à points (catalogue, propagation, recherche, modes de points) | `panel/sidebar/grades/scoredcomments/*` (le lecteur de « texte (−0,5) » est gardé pour la migration) |
| Élément commentaire à points | `document/editions/elements/ScoredCommentElement.java` (lu à l'ouverture, réécrit en texte simple) |
| Mode des points par exercice / classe / copie | `pointsMode`, `pointsAutoMode`, `pointsStart` de `GradeElement` (lus, jamais réécrits) |
| Suggestions de commentaires par note, flux « correction par exercice » ancien | `CommentSuggestions`, `ExerciseCorrectionWorkflow` (à confirmer à la lecture) |

**Gardé** : barème, notes, échelle de notes (`MarkScale`), export Moodle (le retour = les lignes), nouvelle
évaluation depuis le scan, pages des exercices, notes du prof, onglet texte, marques rapides (trait, ✓, ✗, cercle),
présentation (PNF) corrigée.

## 6. Migration des copies déjà corrigées (1.6.0 / 1.7.0) : rien ne bouge

1. **Avant** : `scripts/verify_grades.py` sur le dossier, corriger à la main les cas signalés (voir
   `docs/verification-notes.md`), sauvegarde du dossier.
2. **À l'ouverture en 1.8.0** : chaque commentaire à points / pastille devient un texte simple, au même endroit,
   même texte ; chaque note garde **exactement** sa valeur et devient « ✎ tapée (ancienne correction) » ; le champ de
   la sous-question est pré-rempli avec les lignes retrouvées (« Erreur formule −0,5 »), marqué « importé ». Rien
   n'est recalculé tant que le prof ne modifie pas le champ.
3. Test : les copies fictives notées en 1.7.0 (pastilles, notes tapées, PNF) ouvertes en 1.8.0 → notes identiques au
   centième, textes identiques.

## 7. Présentation (PNF)

- Une marque retient son exercice ; le tableau de la page 1 est **recalculé** à partir des marques présentes :
  supprimer une marque retire sa barre (B16).
- Liste des PNF de la copie dans le panneau, avec ✕ et clic pour la voir.
- Note PNF : calculée seulement quand une marque est ajoutée ou retirée ; une note tapée gagne (B2). Barème réglable
  (B17).
- Saisie du commentaire dans une petite fenêtre, pas dans un menu (touches mortes `^` `` ` ``, B14) ; supprimer un
  commentaire utilisé demande confirmation avec le nombre d'utilisations (B15).

## 8. Étapes, chacune livrée et vérifiée

| # | Étape | Vérification |
|---|---|---|
| 0 | **1.7.1 d'urgence** (optionnel, si la correction en cours continue en 1.7) : B1 et B2 seulement | ouverture ×20 sans changement (sandbox) |
| 1 | Moteur de calcul des lignes, pur et sans JavaFX (`LineGrading`) + lecture/écriture dans le fichier de correction | tests unitaires de toutes les règles du §2, invariants |
| 2 | Retrait des fonctions (§5) + migration (§6) | copies fictives 1.7 → 1.8 : notes identiques |
| 3 | Panneau de correction : un champ par exercice, blocs, ce que Corrigo a compris à droite (§1), navigation clavier | scénario au clavier sur ALPHA…HOTEL, captures |
| 4 | Lignes déjà utilisées, complétion, équité (§3) | tests unitaires de la banque, scénario « harmoniser » |
| 5 | Placement au clic, colonne automatique, place libre sans les taches du scan (§4, B18) | captures sur copies fictives scannées (`make_demo_scan.py`) |
| 6 | PNF (§7) | scénario ajout/suppression de marques, note tapée gardée |
| 7 | Édition des textes dans la vue de correction, curseur visible, tailles réglables (B21–B23) | scénario |
| 8 | Plus tard : recalage du barème dans le tableau scanné (B19) ; grille fine importée du corrigé (skill qui lit un corrigé de n'importe quel format et propose les lignes de chaque sous-question) | — |

Chaque étape : tests unitaires, essai dans le sandbox (copies fictives seulement), un commit, poussé sur la branche
de travail ; fusion dans `master` par Nathan.

**Objectif de vitesse** : une sous-question corrigée en moins de 5 secondes au clavier quand la ligne existe déjà
(taper 3 lettres, Tab, Entrée).

## 9. Questions encore ouvertes

1. ✅ 1.7.1 faite (B1, B2).
2. Les points « (−0,5) » sont-ils écrits sur la copie par défaut ?
3. Mots « tous les points » et « aucun point » : la liste du §2 te va ?
4. ✅ Une sous-question sans ligne n'est pas notée.
