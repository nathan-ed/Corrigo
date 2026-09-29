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

## Build and run

JDK 21 is needed (in `JAVA_HOME`).

```
./gradlew run      # run the application
./gradlew test     # unit tests
./gradlew jpackage # installer for the current platform (see build.gradle, autoPackage)
python3 scripts/build_user_guide.py   # HTML and PDF user guides, from docs/ (needs python-markdown and Chromium)
```

The main libraries are JavaFX, Apache PDFBox, JMetro, ControlsFX, JLaTeXMath, SnakeYAML and OpenCSV (see
`build.gradle`).

## License

Apache License 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE).
