# Annoter les copies

Tout ce qui est décrit ici est une annotation : elle s'affiche sur la copie, est gardée dans le fichier d'édition de la
copie, et n'est écrite dans le PDF qu'à l'export. Pour corriger, il vaut mieux écrire la plupart des commentaires depuis
le [panneau de correction](03-correction.md#le-panneau-de-correction-icône-liste), qui les place à côté de la note et
propose les commentaires déjà utilisés ; les outils ci-dessous restent pour les annotations libres.

## Textes (onglet T)

- **Ajouter un texte** : double-clic sur la page, ou `Ctrl+T` à la position de la souris. Écrivez dans le champ de
  texte de l'onglet Textes.
- **Déplacer / redimensionner** : glissez le texte. Le trait plein à sa droite règle sa largeur maximale (il passe à la
  ligne au-delà).
- **Style** : police (y compris celles du système), taille, gras, italique et couleur dans l'onglet Textes.
  `Ctrl+Alt++` / `Ctrl+Alt+-` changent la taille du texte sélectionné ; `Ctrl+Alt+1…9` sa couleur.
- **Formules** : écrivez les maths entre `$…$`, comme en LaTeX : `la racine est $x = \frac{-b}{2a}$`. Le bouton
  **∑** fait de tout le texte une formule. Les anciens textes écrits avec `$$` ou LibreOffice Math `&&` s'affichent
  toujours.
- **Liens** : un texte qui commence par `www.`, `http://` ou `https://` devient un lien cliquable dans le PDF exporté.
- **Dupliquer** : double-clic sur un texte. **Envoyer vers la page...** (clic droit) le déplace sur une autre page.
- **Copier vers fichiers** : met le texte sélectionné sur les autres copies ouvertes, à la même page et au même
  endroit.

### Listes de commentaires

L'onglet Textes garde des listes des textes que vous avez écrits, pour les réinsérer :

- **Cette évaluation** : les commentaires écrits sur les copies de l'évaluation, regroupés par exercice (lus dans
  toutes les copies du dossier). Le nombre indique sur combien de copies se trouve un commentaire. Clic droit sur un
  commentaire pour :
  - **Où est-il écrit ?** : les aperçus de toutes les copies où il se trouve, zoomables autour du commentaire ; un
    clic ouvre la copie à cette page ;
  - **Déplacer vers l'exercice** : quand il a été classé dans le mauvais exercice ;
  - **Retirer de cette liste** : le masque (il reste sur les copies).
- **Éléments Favoris** : vos textes réutilisables. `Ctrl+1…9` insère l'un des neuf premiers à la souris.
- **Éléments Précédents** : les textes écrits récemment.
- **Éléments sur ce document** : les textes de la copie ouverte.

En écrivant dans le champ de texte, les textes semblables des listes sont mis en évidence ; les flèches et `Entrée` en
insèrent un. Un clic sur un élément de liste l'insère ; `Maj+clic` l'insère *lié* (le modifier dans la liste le modifie
sur la page). Les listes peuvent être triées, enregistrées et rechargées (icônes de sauvegarde et de liste).

## Dessins, figures et images (onglet Dessin)

*L'onglet Dessin est masqué pour le moment : cette partie le décrit pour plus tard.*

- **Dessin à main levée** : **Nouveau dessin à main levée**, ou `Ctrl+D` pour commencer un dessin sur la page. En mode
  dessin, `Maj` (ou `L` maintenu) trace des lignes droites, `M` (ou `P` maintenu) des lignes horizontales/verticales ;
  `Retour arrière` annule le dernier trait ; `Échap`, un clic droit ou un double-clic quittent le mode. L'écriture
  manuscrite longue est coupée en plusieurs éléments (préférences **Écriture manuscrite**).
- **Figures vectorielles** : figures favorites, figures précédentes, **Charger les figures par défaut**, ou ouvrir un
  fichier SVG.
- **Images** : images favorites, et une **Galerie** des dossiers d'images que vous y ajoutez.
- Une figure ou une image peut avoir son propre raccourci clavier (clic droit → **Modifier le raccourcis clavier**).

## Sélectionner et modifier des éléments

Cliquez sur un élément pour le sélectionner ; `Ctrl+A` sélectionne tous les éléments de la page, `Suppr` supprime la
sélection. `Ctrl+X`, `Ctrl+C` / `Ctrl+V` coupent, copient et collent. Clic droit sur un élément pour son menu (favoris,
supprimer, dupliquer, envoyer vers une page…).

## Pages (mode Édition des pages)

Chaque page a des boutons sur le côté : monter/descendre, tourner à gauche/à droite, supprimer, ajouter des pages
(blanche, d'un autre PDF, ou images converties), capturer en image, recadrer ou ajouter des marges. **Édition des
pages** dans la barre du bas affiche les pages en grille : sélectionnez-en plusieurs (`Ctrl+clic`, `Maj+clic`) et
glissez-les pour les réordonner.

Ces actions modifient le fichier PDF lui-même, immédiatement (annulez-les avec `Ctrl+Z` en mode Édition des pages).

## Outils PDF

Dans le menu **Outils** (et son sous-menu **Outils de PDF**) :

- **Convertir des images en PDF** (`Ctrl+Maj+C`) : des images en un PDF, ou chaque sous-dossier d'un dossier en son
  propre PDF (une page par image), avec le format de page et la résolution de votre choix. Sert aussi à ajouter des
  pages converties à une copie.
- **Diviser un PDF** (trois entrées dans **Outils de PDF**) : par pages de séparation d'une couleur (glissez des
  feuilles foncées entre les élèves au scan), toutes les *n* pages, ou aux pages sélectionnées, et nommez les fichiers
  obtenus (les noms peuvent être importés d'un fichier).
- **Créer ou démonter un livret** (**Outils de PDF**) : avec des options pour les copies doubles d'examen et l'ordre
  inversé.
- **Ajouter des marges** (**Outils de PDF**) : des marges autour des pages, ou des marges négatives pour les recadrer.
- **Capturer (Image)** (bouton sur le côté d'une page) : exporte une page, une sélection ou tout le document en images
  PNG.
- **Exporter/Importer des éditions/barèmes** : enregistrer les annotations ou le barème de copies dans un fichier, et
  les charger sur d'autres copies.
- **Éditions des documents du même nom** : récupérer les annotations d'une copie déplacée ou renommée hors de
  l'application.
- **Supprimer les éditions des fichiers ouverts** : enlève toutes les annotations des copies de la liste (les PDF ne
  changent pas).
