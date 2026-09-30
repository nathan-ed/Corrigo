<p align="center">
  <img src="src/main/resources/logo.png" alt="Corrigo" width="120" height="120"><br>
  <b>Corrigo</b><br>
  Correction de copies scannées, exercice par exercice
</p>

[English version](README.md)

Corrigo est une application de bureau pour corriger des copies scannées : annoter les PDF, noter exercice par exercice
avec des commentaires réutilisables, étiqueter les méthodes et les erreurs de chaque copie (elles peuvent donner ou
retirer des points), les voir pour toute la classe, et rendre les copies en PDF ou via Moodle. Les annotations ne
modifient jamais les PDF d'origine.

- [Guide d'utilisation](docs/guide-utilisateur/README.md)
- [User guide (English)](docs/user-guide/README.md)

## En images

**1. Une nouvelle évaluation à partir du scan de la classe** : les élèves dans l'ordre du scan, un PDF par élève, le
barème avec ses exercices, ses pages et ses critères.

[![Nouvelle évaluation](docs/videos/new-evaluation-fr.webp)](docs/videos/new-evaluation-fr.mp4)

**2. Corriger un exercice sur toutes les copies** : pointer une erreur et appuyer sur `#`, ses points comptent ; un
commentaire pour l'élève ; la méthode de la copie suivante ; les commentaires déjà écrits sont proposés.

[![Correction](docs/videos/grading-fr.webp)](docs/videos/grading-fr.mp4)

**3. La classe** : méthodes et erreurs avec la moyenne des points, toutes les copies ayant une erreur côte à côte, une
note personnelle avec capture d'écran, revue des copies une à une, étiquetage des copies sans les ouvrir.

[![La classe](docs/videos/class-fr.webp)](docs/videos/class-fr.mp4)

Cliquez sur une animation pour la vidéo complète. Les copies des vidéos et des captures sont fictives (élèves portant
des noms de mathématiciens).

## Installation

Téléchargez l'installateur de votre système (Windows, macOS, Linux) dans les
[versions](https://github.com/nathan-ed/Corrigo/releases), ou lancez Corrigo depuis le code source (Java 21 et Git
requis) :

```
git clone https://github.com/nathan-ed/Corrigo.git
cd Corrigo
./gradlew run        # Windows : gradlew.bat run
```

Vos réglages sont conservés dans `~/.local/share/Corrigo` (Linux), `~/Library/Application Support/Corrigo` (macOS) ou
`%APPDATA%\Corrigo` (Windows) ; les annotations et données de chaque évaluation sont à côté de ses copies. Le premier
démarrage reprend les réglages de PDF4Teachers s'il était installé.

## Basé sur PDF4Teachers

Corrigo est une version modifiée de [PDF4Teachers](https://github.com/ClementGre/PDF4Teachers), de Clément Grennerat
et des contributeurs de PDF4Teachers, sous licence Apache 2.0. Il n'est ni conçu ni approuvé par les auteurs de
PDF4Teachers. Voir [NOTICE](NOTICE).

## Licence

Apache License 2.0 : voir [LICENSE](LICENSE) et [NOTICE](NOTICE).
