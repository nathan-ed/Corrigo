---
name: corrigo-test
description: Tester, vérifier ou lancer Corrigo (ce dépôt) : uniquement en sandbox, sur des copies fictives. À charger avant tout test, contrôle, lancement de l'application ou analyse de données de correction. Interdit absolu de toucher aux vraies données des élèves.
---

# Tester Corrigo : toujours en sandbox, jamais les vraies données

## Toujours ALPHA, BRAVO, CHARLIE… et rien d'autre

Tout test, contrôle, scénario ou capture se fait UNIQUEMENT sur la classe fictive générée par
`scripts/make_test_eval.py` : copies 01_ALPHA, 02_BRAVO, 03_CHARLIE… 08_HOTEL (Classe-A), INDIA et
JULIETT (Classe-B), dans un sandbox sous `/tmp`. C'est toujours ça, sans exception.

- On ne cherche jamais autre chose : pas de `find`, `ls`, `grep` ou recherche dans le dossier personnel
  pour trouver des évaluations, des copies, des PDF ou des données de l'application.
- On ne demande pas « sur quelle évaluation tester » : la réponse est toujours ALPHA, BRAVO, CHARLIE…
- Dans les messages, les tests, les commits et les captures : uniquement ces noms fictifs.

## Règle absolue (protection des données des élèves)

JAMAIS les vraies données. JAMAIS. Même en lecture seule, même « juste pour vérifier ».

Interdit, sans exception :

- lire, lister, ouvrir, copier, analyser ou lancer l'application sur les vraies évaluations :
  `~/Desktop/Evaluations/` et tout dossier de copies réelles d'élèves (PDF, `.*.pdf.yml`, `.pdf4teachers/`) ;
- lire ou copier les données réelles de l'application : `~/.local/share/Corrigo/`, `~/.local/share/PDF4Teachers/`
  (`userdata.yml`, `evaluations.yml`, `settings.yml`… contiennent des noms et des chemins d'élèves) ;
- copier ces données « dans le sandbox » ou dans `/tmp` : une copie de vraies données reste de vraies données ;
- afficher un nom d'élève, une note réelle ou un chemin de copie réelle dans le chat, un log, un commit.

Si une question semble demander les vraies données (compatibilité d'une évaluation en cours, notes
existantes…) : ne pas regarder. Reproduire le cas sur des copies fictives, ou demander à Nathan de
décrire la situation.

## Comment tester

Toujours sur des copies fictives, dans un sandbox isolé (voir aussi `DEV_NOTES.md`, section Testing) :

1. Copies fictives : `python3 scripts/make_test_eval.py <tmp>/eval` (ou `scripts/make_demo_scan.py` pour les démos).
   Pour reproduire un cas réel, le recréer à la main dans ces copies fictives (barème, commentaires, tags).
2. Build isolé : init script `allprojects { layout.buildDirectory.set(file("<tmp>/build")) }`.
3. Données de l'app isolées : `XDG_DATA_HOME=<tmp>/xdg`, avec un `settings.yml` écrit à la main
   (`storeEditionsNextToPdf: true`, `checkUpdates: false`, `sendStatistics: false`) et un `userdata.yml`
   qui ne liste que les copies fictives. Ne jamais partir des fichiers de `~/.local/share/Corrigo/`.
4. Écran virtuel : `Xvfb :77 …`, puis
   `env -u SESSION_MANAGER -u DBUS_SESSION_BUS_ADDRESS DISPLAY=:77 XDG_DATA_HOME=<tmp>/xdg dbus-run-session -- ./gradlew --no-daemon --init-script <tmp>/isolated-build.gradle run`.
   Jamais sur l'écran réel.
5. Tests unitaires : `./gradlew --init-script <tmp>/isolated-build.gradle test`.
6. À la fin, supprimer le sandbox.
