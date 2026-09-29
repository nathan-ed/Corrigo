# Préférences, données et problèmes

## Préférences (barre de menus → Préférences...)

- **Accessibilité** : langue.
- **Ergonomie** : thème sombre ou thème du système, toujours restaurer la session précédente, effets de zoom/défilement.
- **Sauvegarde et éditions** : sauvegarder automatiquement, sauvegarder régulièrement, **Enregistrer les éditions à côté
  des fichiers PDF** (recommandé : les annotations suivent alors le dossier).
- **Menu contextuel des pages** : ce que propose le clic droit sur une page.
- **Listes d'éléments** et **Éléments textuels** : affichage des listes, nombre maximal de textes précédents,
  comportement des favoris, largeur maximale par défaut d'un nouveau texte.
- **Écriture manuscrite** : quand un dessin à main levée est coupé en plusieurs éléments (distance, longueur, durée).
- **Réseau** : alerte de mise à jour.
- Derniers groupes : conseils automatiques ; zoom de l'application, zoom du rendu des PDF (pages plus nettes, au prix de
  la mémoire), rendu adapté au zoom, et une correction pour les menus qui ne s'ouvrent pas avec certains gestionnaires de
  fenêtres Linux.

## Où sont les données

**Dans chaque dossier d'évaluation** (à côté des copies) :

```
07_DUPONT.pdf              la copie (pas modifiée par les annotations)
.07_DUPONT.pdf.yml         ses annotations et ses notes
.pdf4teachers/
  comments.yml             les commentaires de l'évaluation, par exercice
  scoredcomments.yml       comment les points comptent par sous-note (« depuis le max » / « depuis 0 »)
  tags.yml                 les méthodes et erreurs, leurs points et commentaires, leur place sur chaque copie
  notes.yml, notes/        les notes personnelles et leurs captures
```

Les fichiers qui commencent par un point sont cachés : affichez les fichiers cachés dans votre explorateur pour les
voir. Copiez, déplacez ou sauvegardez le dossier de l'évaluation en entier : les annotations, commentaires, méthodes et
erreurs et notes personnelles suivent.

**Dans le dossier de données de l'application** (**Outils → Débogue → Ouvrir le dossier de données** ; sous Linux
`~/.local/share/PDF4Teachers`) : les préférences, la liste des fichiers, les listes de textes, les figures et images
favorites, les annotations des copies qui ne sont pas enregistrées à côté de leur PDF, et les notes personnelles prises
sans copie ouverte.

## Problèmes

- **Une copie a perdu ses annotations après avoir été renommée ou déplacée** : renommez les copies depuis
  l'application. Sinon, ouvrez la copie et utilisez **Outils → Éditions des documents du même nom** pour reprendre les
  annotations de l'ancien nom.
- **Une copie ne se charge pas** : le PDF est peut-être abîmé ou illisible. Si seules ses annotations sont abîmées,
  l'application indique où est le fichier d'édition ; corrigez-le ou supprimez-le (**Outils → Débogue → Ouvrir le
  fichier d'édition**).
- **Un commentaire est dans le mauvais exercice dans « Cette évaluation »** : clic droit → **Déplacer vers
  l'exercice**. **Relire les commentaires des copies** depuis le menu de la liste si quelque chose semble dépassé.
- **Méthodes et erreurs rattachées à un ancien nom de fichier** : elles suivent le nom de fichier de la copie ;
  renommez les copies avant de les classer.
- **Autre problème** : **Outils → Débogue → Ouvrir la console d'exécution** montre les messages de l'application ;
  copiez-les pour signaler un problème (**Aide → Demander de l'aide ou signaler un problème (GitHub)**).
