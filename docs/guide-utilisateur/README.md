# Guide d'utilisation de Corrigo

Une application de bureau pour corriger des évaluations scannées : annoter les copies PDF, les corriger exercice par
exercice avec des commentaires réutilisables, voir quelles méthodes et quelles erreurs apparaissent dans la classe, et
rendre les copies en PDF ou par Moodle. Les annotations ne modifient jamais les PDF d'origine : tout ce que vous ajoutez
est gardé à part, et n'est écrit dans de nouveaux fichiers PDF qu'au moment de l'export.

Corrigo est une version modifiée de [PDF4Teachers](https://github.com/ClementGre/PDF4Teachers), sous la même licence
(Apache 2.0). Elle n'est ni faite ni approuvée par les auteurs de PDF4Teachers. À son premier lancement, Corrigo copie
les préférences et les listes de PDF4Teachers s'il était installé ; PDF4Teachers garde les siennes.

## Sommaire

1. [Premiers pas](01-premiers-pas.md) : ouvrir les copies, la fenêtre, sauvegarder, exporter, où sont les données
2. [Annoter les copies](02-annoter.md) : textes et formules, dessins et images, pages, outils PDF
3. [Barème et correction](03-correction.md) : créer le barème, exercices et pages, panneau de correction, commentaires, notes
4. [Méthodes et erreurs](04-methodes-et-erreurs.md) : classer les copies, leurs points et commentaires, pastilles sur la copie, bilan de la classe, parcourir les copies
5. [Notes personnelles](05-notes-personnelles.md) : notes et captures pour vous
6. [Rendre les copies](06-export.md) : export PDF, notes dans un tableur, fichiers de feedback Moodle, compétences
7. [Raccourcis clavier](07-raccourcis.md)
8. [Préférences, données et problèmes](08-preferences-et-donnees.md)

## Une correction type, en bref

1. Scannez toutes les copies en un seul PDF, dans l'ordre de la classe.
2. **Fichier → Nouvelle évaluation…** ([détails](01-premiers-pas.md#une-nouvelle-évaluation-à-partir-du-scan)) : le
   scan, les noms des élèves dans le même ordre, les pages de chaque copie, puis le barème (créé avec l'assistant :
   exercices, leur page, sous-questions ou critères et points, ou importé d'une autre évaluation). Elle écrit un PDF par élève,
   nommé par exemple `07_DUPONT.pdf`, et ouvre le premier dans le panneau de correction.
3. Corrigez ensuite l'exercice 1 sur toutes les copies : points, commentaires,
   méthodes et erreurs (qui peuvent ajouter ou enlever des points). **À corriger ›** (ou `Z`) ouvre la copie suivante au même
   exercice. Puis l'exercice 2, et ainsi de suite.
4. Regardez le bilan de la classe des méthodes et erreurs, vérifiez les copies douteuses avec les aperçus.
5. **Calculer les notes**, puis exportez les copies (**Fichier → Tout exporter**) ou envoyez-les par Moodle
   (**Fichier → Exporter pour Moodle…**).

*English version: [user guide](../user-guide/README.md).*
