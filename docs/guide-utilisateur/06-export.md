# Rendre les copies

## PDF annotés

- **Fichier → Exporter (Régénérer le PDF)** (`Ctrl+E`) : la copie ouverte.
- **Fichier → Tout exporter** (`Ctrl+Maj+E`) : toutes les copies de la liste des fichiers.

Choisissez le dossier de destination et les noms des fichiers (un préfixe/suffixe, ou **Remplacer … par …** dans les
noms), les sortes d'annotations à inclure (textes, notes, dessins, compétences), la qualité des images (dpi), et s'il
faut exporter seulement les copies annotées. Une copie dont le fichier de destination existe déjà est ignorée. Une autre
option n'exporte que les annotations, sans le contenu du PDF d'origine (pour les imprimer sur les copies papier).

Les pastilles des méthodes et erreurs et les notes personnelles ne sont jamais exportées.

## Notes dans un tableur

Icône d'export de l'onglet Barème : exporte les notes de cette copie, ou de toutes les copies de la liste (dans un
seul fichier CSV ou un par copie).

Options : séparateur (virgule ou point-virgule) et langue des nombres/formules ; seulement les copies avec le même
barème, du même dossier, ou entièrement corrigées ; les niveaux du barème à exporter ; une ligne pour le barème et pour
la moyenne ; des lignes pour les commentaires ; **Ajouter une colonne pour la note (1 à 6)**, et **Mettre d'abord à jour
les notes écrites sur les copies**. Le nom de l'élève est tiré du nom du fichier.

## Fichiers de feedback Moodle

**Fichier → Exporter pour Moodle…** crée un zip des copies annotées du dossier de l'évaluation, à déposer dans un devoir
Moodle : chaque élève reçoit sa propre copie corrigée comme fichier de feedback.

Une fois, dans les paramètres du devoir Moodle, activez les types de feedback **Fichiers de feedback** et **Fiche
d'évaluation hors ligne**. Ensuite :

1. Dans le devoir : **Action d'évaluation → Télécharger la fiche d'évaluation**. Elle donne l'identifiant Moodle de
   chaque participant.
2. Préparez un **fichier des élèves** (CSV), un élève par ligne : le nom utilisé dans les noms des copies et le
   courriel, par exemple `DUPONT;felix.dupont@ecole.ch` pour `10_DUPONT.pdf`.
3. **Fichier → Exporter pour Moodle…**, choisissez les deux fichiers. La fenêtre liste chaque copie avec son élève :
   ✓ prête, ⚠ ne peut pas être envoyée (pas dans le fichier des élèves, plusieurs élèves correspondent, plusieurs copies
   pour un élève, pas dans la fiche), – participants sans copie.
4. **Créer le zip** : chaque copie est produite avec toutes ses annotations, nommée comme Moodle l'attend.
5. Dans le devoir : **Action d'évaluation → Déposer plusieurs fichiers de feedback dans un fichier ZIP**, et choisissez
   le zip.

## Compétences

*L'onglet Compétences est masqué pour le moment : cette partie le décrit pour plus tard.*

L'onglet **Compétences** évalue des compétences plutôt que des points : créez une évaluation, listez les compétences et
la façon de noter (caractères, couleurs ou icônes), puis donnez à chaque compétence un niveau sur chaque copie. Un
tableau des compétences peut être placé sur la copie. Les résultats s'exportent en CSV, et les évaluations s'importent
depuis **SACoche** et s'y exportent.
