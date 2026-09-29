<p align="center">
  <img src="src/main/resources/logo.png" alt="Corrigo" width="120" height="120"><br>
  <b>Corrigo</b><br>
  Grading of scanned tests, exercise by exercise
</p>

Corrigo is a desktop application to correct scanned tests: annotate the PDF copies, grade them exercise by exercise
with reusable comments, tag the methods and mistakes of each copy (they can give or remove points), see them for the
whole class, and give the copies back as PDFs or through Moodle. Annotations never modify the original PDFs.

- [User guide (English)](docs/user-guide/README.md)
- [Guide d'utilisation (français)](docs/guide-utilisateur/README.md)

## Based on PDF4Teachers

Corrigo is a modified version of [PDF4Teachers](https://github.com/ClementGre/PDF4Teachers), by Clément Grennerat and
the PDF4Teachers contributors, under the Apache License 2.0. It is not made nor endorsed by the authors of
PDF4Teachers. See [NOTICE](NOTICE): the files changed from PDF4Teachers say so in their header, and the git history
shows every change.

The code of PDF4Teachers keeps its package, `fr.clementgre.pdf4teachers`, so that its later changes can still be
merged. The code written for Corrigo is in `corrigo` (same sub-packages).

## Run it from the source (Linux, macOS, Windows)

There is no installer: Corrigo runs from its source code. You need **Java 21 (a JDK)** and **Git**; Gradle, JavaFX
and the other libraries are downloaded automatically the first time (it takes a few minutes, then starts in seconds).

**1. Install Java 21 and Git**

- **Linux**: `sudo apt install openjdk-21-jdk git` (Debian/Ubuntu), `sudo dnf install java-21-openjdk-devel git`
  (Fedora), `sudo pacman -S jdk21-openjdk git` (Arch).
- **macOS**: `brew install --cask temurin@21` and `brew install git` ([Homebrew](https://brew.sh)), or the installers
  of [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21) and [Git](https://git-scm.com).
- **Windows**: `winget install EclipseAdoptium.Temurin.21.JDK Git.Git` in a terminal, or the installers of
  [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21) (tick *Set JAVA_HOME*) and
  [Git](https://git-scm.com).

Check with `java -version`: it must say 21.

**2. Get the source** (once)

```
git clone https://github.com/nathan-ed/PDF4Teachers.git Corrigo
cd Corrigo
```

**3. Run**

- Linux and macOS: `./gradlew run`
- Windows (PowerShell or cmd): `gradlew.bat run`

**Update** to the latest version: `git pull` in that folder, then run again.

Your settings are kept in `~/.local/share/Corrigo` (Linux), `~/Library/Application Support/Corrigo` (macOS) or
`%APPDATA%\Corrigo` (Windows); the annotations and data of each evaluation are next to its copies. The first start
copies the settings of PDF4Teachers if it was installed.

### For development

```
./gradlew test     # unit tests
python3 scripts/build_user_guide.py   # HTML and PDF user guides, from docs/ (needs python-markdown and Chromium)
```

The main libraries are JavaFX, Apache PDFBox, JMetro, ControlsFX, JLaTeXMath, SnakeYAML and OpenCSV (see
`build.gradle`).

## License

Apache License 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE).
