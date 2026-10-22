#!/usr/bin/env python3
"""Check this checkout is ready to build, configure and run Grok Home. Safe to run any time; it changes nothing.

    python3 scripts/doctor.py            # human readable
    python3 scripts/doctor.py --json     # machine readable, for an AI agent

Each check is PASS, WARN (works, but something is missing or optional) or FAIL (fix before continuing).
Every non-PASS line says exactly what to run. Exit code is 1 if anything FAILed, otherwise 0.
Nothing here contacts a bot or sends a message.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "backend/bot-bridge"
IDENTITY = ROOT / "packages/apps/GrokHome/src/dev/grokhome/BotIdentity.java"
SHAPES = {"sphere", "star", "heart", "capsule", "cloud", "flower", "hexagon", "square", "triangle"}
checks = []


def add(name, status, detail, fix=""):
    checks.append({"check": name, "status": status, "detail": detail, "fix": fix})


def run(cmd, timeout=20):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, cwd=str(ROOT))
        return p.returncode, (p.stdout + p.stderr).strip()
    except (OSError, subprocess.TimeoutExpired) as e:
        return 127, str(e)


def version_tuple(text):
    m = re.search(r"(\d+)\.(\d+)(?:\.(\d+))?", text)
    return tuple(int(x or 0) for x in m.groups()) if m else (0, 0, 0)


def check_python():
    v = sys.version_info
    ok = (v.major, v.minor) >= (3, 10)
    add("python", "PASS" if ok else "FAIL", f"Python {v.major}.{v.minor}.{v.micro}", "" if ok else "Install Python 3.10 or newer.")


def check_java():
    java = shutil.which("java")
    if not java:
        return add("java", "FAIL", "java not found", "Install JDK 17 and put it on PATH (or set JAVA_HOME).")
    _, out = run([java, "-version"])
    major = version_tuple(out)[0]
    if major == 1:
        major = version_tuple(out)[1]
    ok = major == 17
    add("java", "PASS" if ok else "WARN", f"java {major}", "" if ok else "The project builds with JDK 17. Set JAVA_HOME to a JDK 17 install.")


def sdk_dir():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key])
    props = ROOT / "local.properties"
    if props.exists():
        m = re.search(r"^sdk\.dir=(.+)$", props.read_text(encoding="utf-8"), re.M)
        if m:
            return Path(m.group(1).strip().replace("\\:", ":").replace("\\\\", "\\"))
    return None


def check_sdk():
    sdk = sdk_dir()
    if not sdk or not sdk.exists():
        return add("android-sdk", "FAIL", "Android SDK not found",
                   "Install the Android SDK (platform 34 + build-tools), then set ANDROID_HOME or create local.properties with sdk.dir=/absolute/path/to/sdk")
    has_platform = (sdk / "platforms/android-34").exists()
    has_tools = any((sdk / "build-tools").glob("*")) if (sdk / "build-tools").exists() else False
    if has_platform and has_tools:
        add("android-sdk", "PASS", f"{sdk} (platform 34, build-tools)")
    else:
        add("android-sdk", "FAIL", f"{sdk} is missing " + ("platform android-34 " if not has_platform else "") + ("build-tools" if not has_tools else ""),
            "sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\"")


def check_adb():
    adb = shutil.which("adb")
    sdk = sdk_dir()
    if not adb and sdk:
        for cand in (sdk / "platform-tools/adb", sdk / "platform-tools/adb.exe"):
            if cand.exists():
                adb = str(cand)
    if adb:
        add("adb", "PASS", adb)
    else:
        add("adb", "WARN", "adb not found (only needed to install on a device)", "Install Android platform-tools and put adb on PATH.")


def check_node():
    node = shutil.which("node")
    if not node:
        return add("node", "FAIL", "node not found", "Install Node.js 22.13 or newer.")
    _, out = run([node, "--version"])
    ok = version_tuple(out) >= (22, 13, 0)
    add("node", "PASS" if ok else "FAIL", out, "" if ok else "Install Node.js 22.13 or newer.")


def check_bridge_deps():
    bdk = BRIDGE / "node_modules/.bin" / ("bdk.cmd" if os.name == "nt" else "bdk")
    if bdk.exists():
        add("bridge-dependencies", "PASS", "backend/bot-bridge/node_modules installed")
    else:
        add("bridge-dependencies", "FAIL", "bridge dependencies are not installed", "cd backend/bot-bridge && npm ci")


def declared_names():
    if not IDENTITY.exists():
        return None
    m = re.search(r"NAMES=\{([^}]*)\}", IDENTITY.read_text(encoding="utf-8"))
    return re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)) if m else None


def check_roster():
    local = BRIDGE / "bots.local.json"
    example = BRIDGE / "bots.example.json"
    if not local.exists():
        return add("bots.local.json", "FAIL", "no roster yet (backend/bot-bridge/bots.local.json is missing)",
                   "Write a spec of YOUR bots and run: python3 scripts/roster/sync_roster.py my-bots.json --apply   (see docs/ROSTER.md)")
    try:
        roster = json.loads(local.read_text(encoding="utf-8"))
        names = [b["name"] for b in roster]
        assert names and all(isinstance(n, str) and n == n.strip() and n for n in names)
        assert len({n.lower() for n in names}) == len(names), "duplicate names"
        for b in roster:
            assert b.get("shape", "sphere") in SHAPES, f"{b['name']}: unknown shape"
            assert re.fullmatch(r"#[0-9A-Fa-f]{6}", b["color"]), f"{b['name']}: color must look like #RRGGBB"
    except Exception as e:  # noqa: BLE001
        return add("bots.local.json", "FAIL", f"invalid roster: {e}", "Fix it, or regenerate with scripts/roster/sync_roster.py (docs/ROSTER.md)")
    add("bots.local.json", "PASS", f"{len(names)} bots: {', '.join(names)}")
    try:
        sample = {b["name"] for b in json.loads(example.read_text(encoding="utf-8"))}
        if set(names) == sample:
            add("roster-is-yours", "WARN", "the roster is still the example one; those names are not real bots on your account",
                "Replace it with the exact names of bots you own (docs/ROSTER.md). Unknown names may make the upstream service create an empty bot.")
        else:
            add("roster-is-yours", "PASS", "roster differs from the example")
    except Exception:  # noqa: BLE001
        pass
    declared = declared_names()
    if declared is None:
        add("roster-in-app", "WARN", "could not read BotIdentity.java")
    elif declared == names:
        add("roster-in-app", "PASS", "app identities match bots.local.json")
    else:
        add("roster-in-app", "FAIL", "BotIdentity.java does not match bots.local.json (the app would show different bots than the bridge allows)",
            "python3 scripts/roster/sync_roster.py <spec.json> --apply   (docs/ROSTER.md)")


def check_wake_model():
    model = ROOT / "app/src/main/assets/vosk-model-small-en-us-0.15.zip"
    if model.exists():
        add("wake-word-model", "PASS", "offline speech model present")
    else:
        add("wake-word-model", "WARN", "optional offline speech model is missing; the 'Hey Bots' wake word will be unavailable",
            "python3 scripts/fetch-wake-model.py   (about 40 MB), then rebuild")


def check_login():
    bdk = BRIDGE / "node_modules/.bin" / ("bdk.cmd" if os.name == "nt" else "bdk")
    if not bdk.exists():
        return
    code, out = run([str(bdk), "whoami", "--json"], timeout=30)
    if code == 0:
        add("cursor-sign-in", "PASS", "signed in to Cursor BDK on this computer")
    else:
        add("cursor-sign-in", "WARN", "not signed in (or BDK could not reach Cursor)", "cd backend/bot-bridge && npx bdk login   (a person must complete the browser sign-in)")


def probe(url):
    try:
        with urllib.request.urlopen(url, timeout=2) as r:
            return r.status
    except Exception:  # noqa: BLE001
        return None


def check_services():
    bridge = probe("http://127.0.0.1:8790/v1/tools")
    add("bridge-running", "PASS" if bridge else "WARN", "bridge answers on 127.0.0.1:8790" if bridge else "bridge is not running",
        "" if bridge else "bash scripts/start-bot-bridge.sh")
    gateway = probe("http://127.0.0.1:8787/health")
    add("gateway-running", "PASS" if gateway else "WARN", "gateway answers on 127.0.0.1:8787" if gateway else "gateway is not running",
        "" if gateway else "python3 backend/server.py --enable-bots --disable-xai")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    for fn in (check_python, check_java, check_sdk, check_adb, check_node, check_bridge_deps, check_roster,
               check_wake_model, check_login, check_services):
        fn()
    failed = [c for c in checks if c["status"] == "FAIL"]
    if args.json:
        print(json.dumps({"ok": not failed, "checks": checks}, indent=2))
    else:
        for c in checks:
            print(f"[{c['status']:<4}] {c['check']}: {c['detail']}")
            if c["fix"] and c["status"] != "PASS":
                print(f"         fix: {c['fix']}")
        print("\n" + ("Ready to build." if not failed else f"{len(failed)} blocking problem(s). Fix the FAIL lines first."))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
