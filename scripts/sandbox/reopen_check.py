#!/usr/bin/env python3
"""SANDBOX ONLY, fictional copies only (see .claude/skills/corrigo-test): opening a copy must never change it.

For each copy of the folder, several times: starts Corrigo on a virtual display with the copy, waits for it, saves
(Ctrl+S), stops it, and compares its edit file with the one before (everything but the scroll position). Any difference
is a bug: the copy changed only because it was opened.

    python3 scripts/make_reopen_cases.py <tmp>/cases
    python3 scripts/sandbox/reopen_check.py <tmp>/cases/Classe-A/Test-1 --data <tmp>/xdg --init-script <tmp>/isolated-build.gradle

--data is the XDG_DATA_HOME of the sandbox (never the real one); --rounds (default 2); --display (default :77).
Needs Xvfb and python-xlib (pip install python-xlib)."""
import argparse, glob, os, shutil, signal, subprocess, sys, time, yaml

# The YAML written by Corrigo (Java) may have a text "=" unquoted: read as a string, not as the YAML "value" tag
class CorrigoLoader(yaml.SafeLoader):
    pass
CorrigoLoader.add_constructor("tag:yaml.org,2002:value", lambda loader, node: loader.construct_scalar(node))


def load_yaml(f):
    return yaml.load(f, Loader=CorrigoLoader)


HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))


def snapshot(edit_file):
    with open(edit_file, encoding="utf-8") as f:
        base = load_yaml(f) or {}
    base.pop("lastScrollValue", None)
    return base


def diff(a, b, path=""):
    if isinstance(a, dict) and isinstance(b, dict):
        out = []
        for key in sorted(set(a) | set(b), key=str):
            if key not in a: out.append("%s/%s added: %r" % (path, key, b[key]))
            elif key not in b: out.append("%s/%s removed: %r" % (path, key, a[key]))
            else: out += diff(a[key], b[key], "%s/%s" % (path, key))
        return out
    if isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b): return ["%s: %d → %d items: %r → %r" % (path, len(a), len(b), a, b)]
        out = []
        for i, (x, y) in enumerate(zip(a, b)): out += diff(x, y, "%s[%d]" % (path, i))
        return out
    return [] if a == b else ["%s: %r → %r" % (path, a, b)]


# What makes a grade or what the student reads: any change is a bug. The rest (positions moved by a rounding of a
# grid unit, version of the file, mode kept for the old methods and mistakes) is shown as information.
CRITICAL = {"value", "total", "text", "comment", "points", "noPoints", "correctionLine", "pnfExercise", "valueSource",
            "valueBeforeComments", "pointsStart", "corrections", "outOfTotal", "name", "parentPath"}


def is_critical(change):
    if " items: " in change or " added: " in change or " removed: " in change:
        key = change.split(" ")[0].rsplit("/", 1)[-1]
        return not (key in ("pointsAutoMode", "versionID") and (" added: " in change or " removed: " in change))
    key = change.split(":")[0].rsplit("/", 1)[-1].split("[")[0]
    return key in CRITICAL or "/corrections" in change.split(":")[0]


def open_and_save(pdf, args, log):
    env = dict(os.environ, DISPLAY=args.display, XDG_DATA_HOME=args.data, HOME=os.path.join(args.data, "home"))
    env.pop("SESSION_MANAGER", None)
    env.pop("DBUS_SESSION_BUS_ADDRESS", None)
    os.makedirs(env["HOME"], exist_ok=True)
    command = ["dbus-run-session", "--", os.path.join(REPO, "gradlew"), "--no-daemon", "-q"]
    if args.offline: command.append("--offline")
    if args.init_script: command += ["--init-script", args.init_script]
    command += ["run", "--args=" + pdf]
    with open(log, "w") as out:
        app = subprocess.Popen(command, cwd=REPO, env=env, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        for _ in range(240):
            time.sleep(1)
            with open(log, errors="replace") as f:
                if "toOpenPath" in f.read(): break
        time.sleep(args.wait)  # The copy is opened, its edition loaded, the late tasks done
        subprocess.run([sys.executable, os.path.join(HERE, "xdo.py"), "key", "ctrl+s"], env=env, check=True)
        time.sleep(4)
    finally:
        os.killpg(app.pid, signal.SIGTERM)
        try:
            app.wait(20)
        except subprocess.TimeoutExpired:
            os.killpg(app.pid, signal.SIGKILL)
        time.sleep(2)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder")
    parser.add_argument("--data", required=True, help="XDG_DATA_HOME of the sandbox")
    parser.add_argument("--init-script", default=None)
    parser.add_argument("--display", default=":77")
    parser.add_argument("--rounds", type=int, default=2)
    parser.add_argument("--wait", type=int, default=15)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--only", default=None, help="only the copies whose name contains this")
    args = parser.parse_args()
    if ".local/share" in os.path.abspath(args.data) and "tmp" not in os.path.abspath(args.data):
        sys.exit("--data must be a sandbox folder, never the real data of the application")

    # Settings of the sandbox: the copy given is opened, the editions are next to the PDF, nothing is sent
    settings_dir = os.path.join(args.data, "Corrigo")
    os.makedirs(settings_dir, exist_ok=True)
    settings_file = os.path.join(settings_dir, "settings.yml")
    settings = {}
    if os.path.exists(settings_file):
        with open(settings_file) as f: settings = yaml.safe_load(f) or {}
    settings.update({"restoreLastSession": True, "storeEditionsNextToPdf": True, "checkUpdates": False, "sendStatistics": False})
    with open(settings_file, "w") as f: yaml.safe_dump(settings, f)
    if subprocess.run(["pgrep", "-f", "Xvfb " + args.display], stdout=subprocess.DEVNULL).returncode != 0:
        subprocess.Popen(["Xvfb", args.display, "-screen", "0", "1600x1000x24"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)

    failures = 0
    for pdf in sorted(glob.glob(os.path.join(args.folder, "*.pdf"))):
        name = os.path.basename(pdf)
        if args.only and args.only not in name: continue
        edit_file = os.path.join(args.folder, "." + name + ".yml")
        if not os.path.exists(edit_file): continue
        before = snapshot(edit_file)
        shutil.copy(edit_file, edit_file + ".before")
        critical, minor = [], []
        for round_number in range(1, args.rounds + 1):
            open_and_save(pdf, args, os.path.join(args.data, "run.log"))
            changes = diff(before, snapshot(edit_file))
            critical = [c for c in changes if is_critical(c)]
            minor = [c for c in changes if not is_critical(c)]
            if critical: break
        status = "CHANGED at opening %d" % round_number if critical else "OK" + (" (details: %d)" % len(minor) if minor else "")
        print("%-16s %s" % (name, status), flush=True)
        for change in critical[:12]: print("    !! " + change)
        for change in minor[:4]: print("    .. " + change)
        failures += bool(critical)
    print("\n%s" % ("All the copies are unchanged." if failures == 0 else "%d copie(s) changed when opened." % failures))
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
