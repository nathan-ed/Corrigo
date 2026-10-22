# Plan : la correction par lignes (Corrigo 1.8.0)

**But** : corriger vite, de façon équitable, avec un retour pour l'élève. Au clavier : on tape une ligne, ses points,
ligne vide → sous-question suivante, Ctrl+Entrée → copie suivante. Rien d'autre à décider pendant la correction.
**Exigence n°1 : fiabilité.** Une note ne change **jamais** sans un geste du prof sur cette note.

Décisions de Nathan (22 octobre 2026) : mode par sous-question ; « Correct » et « Cohérent » ne diffèrent que par le
texte ; une ligne vide passe seulement d'une sous-question à la suivante ; tailles réglables ; on retire les pastilles,
le mode par exercice, les critères, la galerie… pour ne garder que la correction par lignes (avec les coches et le
placement au clic).

---

## 1. Ce que voit le prof

```
Ex 2 · copie 3/24 (CHARLIE)                         2,5 / 4
┌ Déjà utilisé dans cet exercice ──────────────────────────────┐
│ a) Formule intégrale +1 (14) · Erreur formule −0,5 (9) ·     │
│    Correct (11) · Pas de réponse (2)                         │
│ b) Calcul intégral +2 (12) · Erreur de signe −0,5 (5)        │
└──────────────────────────────────────────────────────────────┘
a) /2                                                 1,5
│ Correct
│ Erreur formule −0,5
b) /2                                                 1
│ Formule intégrale +1
│ err▌ → « Erreur de signe −0,5 »  (Tab pour accepter)
```

- **Un champ par sous-question**, empilés, avec la note calculée à droite.
- **Au-dessus**, toutes les lignes déjà utilisées dans cet exercice, sur toutes les copies, par sous-question,
  triées par fréquence. Un clic ajoute la ligne dans le champ actif.
- **Complétion** : `err` propose la ligne la plus utilisée qui contient ce mot ; Tab l'accepte, points compris.
- **Entrée sur une ligne vide** → sous-question suivante. **Ctrl+Entrée** → copie suivante, même exercice, curseur
  dans le premier champ. **Ctrl+Maj+Entrée** → copie précédente. La page de l'exercice s'affiche toute seule.

## 2. La règle de calcul (une seule, écrite une fois, testée)

Une ligne = `texte [(note privée)] [+/−points]`.
- `+1`, `+0,5` : méthode. `−0,5`, `-1` : erreur. Sans nombre : commentaire seul (aucun point).
- Ce qui est entre parenthèses n'est **jamais** écrit sur la copie.
- **Lignes « tous les points »** : une ligne qui commence par `Correct`, `Cohérent` ou `Juste` (liste réglable).
- **Lignes « aucun point »** : `Pas de réponse`, `Incomplet`, `Insuffisant`, `Réponse erronée`, `Faux` (liste
  réglable). Ce sont des commentaires : la sous-question part de 0.

**Note d'une sous-question** (mode décidé **par sous-question**, par sa première ligne qui compte, comme la règle du
18 octobre) :
1. aucune ligne → la note reste vide (ou celle tapée) ;
2. une ligne « tous les points » **ou** une première ligne à points négative → départ du **maximum** (par erreurs) ;
3. sinon → départ de **0** (par critères) ;
4. note = départ + somme des points, ramenée entre 0 et le maximum ;
5. **une note tapée à la main gagne toujours** : elle est marquée « ✎ tapée », reste tant que le prof ne clique
   pas « ↺ revenir au calcul », et n'est jamais recalculée ni à l'ouverture, ni quand on change une ligne.

**Invariants (tests automatiques)** :
- ouvrir, enregistrer, rouvrir une copie 20 fois → aucun octet de note ne change ;
- la note enregistrée = le calcul des lignes enregistrées, sauf note tapée ;
- changer une ligne de la sous-question a ne change que a (et les sommes) ;
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
| 3 | Panneau de correction par lignes (§1), navigation clavier | scénario au clavier sur ALPHA…HOTEL, captures |
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

1. Étape 0 : faut-il la 1.7.1 d'urgence (tu continues l'évaluation en cours en 1.7) ou tu attends la 1.8 ?
2. Les points « (−0,5) » sont-ils écrits sur la copie par défaut ?
3. Mots « tous les points » et « aucun point » : la liste du §2 te va ?
4. Par erreurs, une sous-question sans aucune ligne : vide (comme aujourd'hui) ou le maximum ?
