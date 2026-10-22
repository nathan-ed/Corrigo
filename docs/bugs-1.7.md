# Bugs et problèmes connus de Corrigo 1.7.0 (commit `67f1aad`, 18 octobre 2026)

Liste complète, pour vérifier **un par un** si une version locale les a déjà corrigés. Chaque bug a :
où il est, comment il se manifeste, et un **test rapide** (une recherche dans le code, ou un scénario d'une minute sur
les copies fictives ALPHA, BRAVO… : jamais sur de vraies copies, voir le skill `corrigo-test`).

Statut : **R** = reproduit dans l'application (sandbox, copies fictives), **C** = reproduit par le calcul (classes
de calcul seules), **L** = trouvé à la lecture du code, **N** = signalé par Nathan.

Pour une IA : « Pour chaque bug de `docs/bugs-1.7.md`, fais le test rapide sur ce dépôt et réponds : encore présent /
corrigé / impossible à dire, avec la ligne de code qui le prouve. Ne lis aucune vraie copie. »

---

## A. Notes qui changent toutes seules (les plus graves)

### B1. Une note tapée à la main sur une sous-question qui a une méthode ou une erreur perd (ou gagne) les points une seconde fois à la réouverture (R)
- **Où** : `ScoredCommentElement.applyTag` → `ScoredComments.recomputeGrade(gradePath, true)`
  (`src/main/java/corrigo/document/editions/elements/ScoredCommentElement.java`, vers la ligne 183). `applyTag` est
  appelé pour chaque pastille de la copie à chaque `TagScoring.syncOpenCopy` : à l'ouverture
  (`ExerciseTags.onEditionLoaded`) et à chaque changement de méthode ou d'erreur, même dans un autre exercice.
- **Effet** : erreur −0,5 sur a (/2), puis 1 tapé à la main → réouverture → a = 0,5. Une seule fois (la note devient
  « calculée » avec `valueBeforeComments: 1`).
- **Test rapide (code)** : dans `applyTag`, l'appel est-il encore `recomputeGrade(gradePath, true)` sans condition ?
  Corrigé si le `true` n'est passé que quand la pastille **commence** à compter (comme `TagEditFiles.update` :
  `if(!noPoints && !counted)`).
- **Test rapide (app)** : sur une copie fictive, poser une erreur sur une sous-question, taper une autre note à la
  main, enregistrer, fermer, rouvrir : la note doit rester celle tapée.

### B2. La note de présentation (PNF) tapée à la main est remplacée à chaque ouverture (R)
- **Où** : `PNFAnnotationManager.updateScore(false)` appelé par `onEditionLoaded` → `updateScoreWhenReady`.
  Condition d'arrêt : `if(!changed && marks == 0 && grade.getCore().getValue() < 0 && !isGradingStarted(grade)) return;`
- **Effet** : PNF tapé 1, aucune marque → réouverture → 2. La valeur tapée n'est gardée nulle part.
- **Test rapide (code)** : `updateScore` peut-il écrire une valeur alors que la note PNF a été tapée à la main ?
  Corrigé si la note PNF n'est recalculée que quand une marque est ajoutée ou supprimée (`changed == true`) et jamais à
  l'ouverture, ou si une valeur tapée est marquée comme telle et respectée.

### B3. Copie commencée en 1.6.0 « par méthodes » : en 1.7.0 une nouvelle méthode donne tous les points (C)
- **Où** : `ScoredCommentGrades.keepStoredValues`, ligne
  `exerciseGrade.put(KEY_AUTO_MODE, ScoredCommentCatalog.Base.FULL.name());` (vers la ligne 336).
- **Effet** : a = +2 depuis 0 (2/4) en 1.6.0 ; en 1.7.0, méthode +1 sur b vide (/6) → b = 6/6, exercice 2 → 8.
- **Test rapide (code)** : le mode gardé est-il toujours `FULL` ? Corrigé s'il est déduit des points (que des
  points positifs → `ZERO`).

### B4. Un « départ » d'une ancienne version revient après une note tapée (C)
- **Où** : `GradeElement.setValueFromComments(false)` (vers la ligne 628) ne remet pas `pointsStart` à -1 ;
  `ScoredCommentGrades.removeEntry(..., keepAsText=true)` laisse la clé `pointsStart` dans le fichier.
- **Effet** : 7 tapé, erreur −1 → 6, puis au calcul suivant → 9 (départ 10 gardé d'une ancienne version).
- **Test rapide (code)** : `setValueFromComments(false)` et `removeEntry(keepAsText)` effacent-ils `pointsStart` /
  `KEY_START` ?

### B5. Un commentaire à 0 point placé en premier fait passer l'exercice « par critères » et met les autres sous-questions à 0 (C)
- **Où** : `ScoredCommentGrades.guessAuto` : `mistake ? FULL : ZERO` (aucun point négatif = par critères).
- **Test rapide (code)** : un point égal à 0 compte-t-il comme une méthode ? Corrigé s'il est ignoré pour le choix.

### B6. Changer le mode de la classe réécrit toutes les copies sur le disque, sans confirmation ni Ctrl+Z (L)
- **Où** : `ScoredComments.setClassMode` → `recomputeOtherCopies` ; aussi depuis le menu d'un commentaire
  (`ScoredCommentPanel`, items `fromMax` / `fromZero` → `setClassMode`).
- **Effet secondaire** : seules les copies de la liste de fichiers sont recalculées ; les autres gardent leurs
  anciennes notes (rien ne recalcule une copie à son ouverture).
- **Test rapide (code)** : `setClassMode` demande-t-il une confirmation ? `ScoredCommentPanel` appelle-t-il encore
  `setClassMode` ?

### B7. Taper une note ne relance pas le calcul (L)
- **Où** : `GradeTreeItemField` (cas `GRADE`) ne rappelle pas `ScoredComments.recomputeExercise`.
- **Effet** : « par critères » choisi pour la classe : sur la copie ouverte, les sous-questions vides restent vides ;
  sur les autres copies (calcul par fichier) elles passent à 0. Affichage seulement : les sommes sont les mêmes.

### B8. Les anciens choix « partir de 0 / du max » par sous-question (1.6.0) restent actifs mais ne sont pas affichés (L)
- **Où** : `ScoredCommentGrades.getChosenMode` (le choix de la sous-question passe avant celui de l'exercise) ;
  `GradingPanel.updatePointsMode` n'affiche que celui de l'exercice.

### B9. Le mode d'un exercice ne se remet pas facilement en automatique (N)
- **Où** : `GradingPanel.showModeMenu` ; le mode automatique reste fixé tant qu'un point compte
  (`pointsAutoMode`).
- **Test rapide (app)** : poser une méthode puis une erreur ; essayer de revenir au mode « par erreurs » pour cette
  sous-question seulement : impossible (le choix est par exercice ou par copie).

### B10. Mode par exercice au lieu de par sous-question (N)
- Demande : le mode (critères / erreurs) doit se choisir **par sous-question** sur la copie.

## B. Attribution des points

### B11. Les points d'une erreur vont à la sous-question la plus proche sur la page, pas à celle sélectionnée (L + N)
- **Où** : `ExerciseTags.addOccurrence` : `String near = getGradeNear(exercise, placement);` puis
  `near != null ? near : getTargetGrade(exercise)`. La sous-question active du panneau ne vient qu'en second.
- **Test rapide (code)** : l'ordre est-il encore « la plus proche, sinon l'active » ? Corrigé si c'est « l'active,
  sinon la plus proche ».

### B12. Critère coché puis sous-question ou exercice renommé : la coche ne compte plus (L)
- **Où** : `EvaluationTags.syncCriteria` crée un nouveau critère pour le nouveau chemin ; l'ancien (utilisé) reste
  avec l'ancien chemin. `ScoredComments.onGradeRenamed` renomme les éléments, pas `tag.criterion` ; `syncOpenCopy`
  supprime alors l'élément et le réécrit sur l'ancien chemin, qui n'existe plus.
- **Test rapide (code)** : y a-t-il un renommage de `criterion` (et de `Use.grade`) quand une note est renommée ?

### B13. Pas d'option « commentaire seulement, sans points » sur une méthode ou une erreur posée (N)
- **Test rapide (app)** : clic droit sur une pastille : l'option existe-t-elle ?

## C. Présentation (PNF)

### B14. `^` et `` ` `` (touches mortes : â, è, ê…) ne marchent pas dans le champ « nouveau commentaire » PNF (N)
- **Où** : `PNFAnnotationManager.buildCommentItems` : `TextField` dans un `CustomMenuItem` d'un menu contextuel (les
  menus ne reçoivent pas la composition des touches mortes).
- **Test rapide (app)** : menu PNF → nouveau commentaire → taper `^` puis `e` : `ê` doit apparaître.

### B15. Un commentaire PNF utilisé se supprime d'un clic, sans confirmation (N)
- **Où** : `PNFAnnotationManager.buildCommentItems`, `forget.setOnMousePressed(e -> { ... forgetComment(comment); ...})`.
- **Test rapide (code)** : y a-t-il une confirmation avant `forgetComment` ?

### B16. Supprimer une marque PNF ne retire pas sa barre du tableau de la page 1 (N + L)
- **Où** : `PNFAnnotationManager.incrementSummaryMark` ajoute une barre ; rien ne l'enlève. La marque ne retient pas
  son exercice : le tableau ne peut pas être recalculé.
- **Test rapide (app)** : poser 2 marques, en supprimer une : le tableau doit montrer 1 barre.
- Demande liée : voir et retirer les PNF une par une dans le panneau (comme les pastilles).

### B17. Le barème PNF est figé à 2 points (L)
- **Où** : `PNFAnnotationManager.score` : `2 - .5 * (marques - 1)`, borné par le total. Avec un exercice PNF sur 4 :
  0 marque → 2/4.

## D. Placement sur la copie

### B18. Les points noirs du scan empêchent de trouver une place libre ; rien n'empêche d'écrire sur la réponse de l'élève en dehors des commentaires de méthodes/erreurs (N + L)
- **Où** : `TagScoring.moveToFreeSpot` + `FreeSpot` : une case de 4 px est « occupée » dès que 2 de ses 9 points sont
  sombres, sans filtre des petites taches ; utilisé seulement pour les commentaires de méthodes et d'erreurs posés à
  un endroit (pas les coches, ni les PNF, ni les commentaires de note).
- **Test rapide (code)** : y a-t-il un filtre des petites taches isolées ? `moveToFreeSpot` est-il utilisé pour les
  autres textes posés automatiquement ?

### B19. Le barème copié sur les autres copies est décalé hors du tableau de la page 1 (scan pas parfaitement aligné) (N)
- **Où** : les notes sont copiées aux mêmes coordonnées sur chaque copie ; aucun recalage sur le tableau scanné.
- **Test rapide (app)** : copie fictive dont la page 1 est décalée de quelques millimètres : les notes doivent
  rester dans les cases.

### B20. Marques rapides : la transparence aux clics est mal rétablie (L)
- **Où** : `QuickMarksBar.setElementsTransparent` : en annulant, **tous** les éléments redeviennent cliquables (même
  ceux qui ne l'étaient pas) ; ceux ajoutés pendant l'attente ne deviennent pas transparents.

## E. Texte et ergonomie

### B21. Éditer un texte fait quitter la vue de correction pour l'onglet Texte (N)
- **Où** : `TextElement.select()` : `SideBar.selectTab(MainWindow.textTab);` (vers la ligne 201) ; aussi
  `PageRenderer` (vers la ligne 456) et `KeyboardShortcuts` (lignes 75 et 298).
- **Test rapide (app)** : dans la vue de correction, cliquer sur un commentaire de la copie : la vue doit rester.

### B22. Pas de curseur visible dans le champ de texte quand on édite un texte (N)
- **Test rapide (app)** : éditer un commentaire depuis le champ : le curseur clignotant doit être visible.

### B23. Commentaires trop gros par défaut (N)
- **Où** : `ScoredComments.FONT` (18), `TagEditFiles.FONT_SIZE` (18), `PNFAnnotationManager.PNF_COMMENT_FONT` (18),
  `QuickGradePlacement.COMMENT_FONT` (16). Pas de réglage.

### B24. Une note corrigée à la main ne reste pas quand on revient sur la copie (N)
- C'est B1 (et B2 pour la PNF).

## F. Mineurs

### B25. `Edition.load` : catalogue de secours d'une autre évaluation (L)
- **Où** : `Edition.java` vers la ligne 91 : `getOrDefault(signature, ScoredComments.getCatalog())` ; à ce moment
  `getCatalog()` utilise encore le barème de la copie précédente.

### B26. Ctrl+Z d'une suppression de méthode/erreur après suppression de la méthode elle-même : occurrence orpheline (L)
- **Où** : `ExerciseTags.removeOccurrence` → `TagUndoAction` → `data.restore(removed)` sans vérifier que le tag existe.

### B27. Par erreurs, une sous-question sans erreur reste vide au lieu de valoir le maximum (question, L)
- **Où** : `ScoredCommentGrades.computeExercise` : seul le mode « par critères » remplit les vides (à 0).
- À décider : voulu ou non ?

---

## Environnement de test (pour reproduire)

- `python3 scripts/make_test_eval.py <tmp>/eval` : copies fictives ALPHA…JULIETT.
- `python3 scripts/verify_grades.py <dossier>` : repère sur les fichiers de correction les cas B1 et B2 (avant et
  après), B3, B4, B5 et les sommes incohérentes (voir `docs/verification-notes.md`).
- Si `clojars.org` et `jitpack.io` sont bloqués (cloud), remplacer dans un sandbox seulement
  `google-diff-match-patch:0.1` et `jSystemThemeDetector:3.9.1` par des versions minimales servies par un dépôt Maven
  local déclaré dans le script d'init Gradle ; le réglage `restoreLastSession: true` doit être dans le
  `settings.yml` du sandbox pour qu'une copie passée en argument soit ouverte.
