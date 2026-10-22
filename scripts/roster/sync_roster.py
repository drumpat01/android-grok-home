#!/usr/bin/env python3
"""Make the Grok Home roster match a list of bots, everywhere it is written down.

    python3 scripts/roster/sync_roster.py SPEC.json            # dry run: shows the plan, writes nothing
    python3 scripts/roster/sync_roster.py SPEC.json --apply    # writes the files
    python3 scripts/roster/sync_roster.py SPEC.json --apply --bump   # also raises versionCode / patch version

SPEC.json is the whole roster you want, in display order:

    [ {"name": "Max", "color": "white", "shape": "sphere"},
      {"name": "Luna", "color": "purple", "shape": "cloud"}, ... ]

- A bot already in the roster may leave out "color" and/or "shape" to keep what it has.
- A new bot must give both. Color is a palette name (White, Brown, Red, Orange, Amber, Green, Teal,
  Blue, Purple, Pink, Gray) or one of the palette hex values. Shape is one the renderer supports.
- Bots missing from SPEC.json are removed. Order in SPEC.json becomes the on-screen order.
- The FIRST bot is the primary: crown, big ambient tile, default selection and voice shortcuts.

Files written (nothing is written unless every check passes):
    backend/bot-bridge/bots.local.json        connector allowlist, color, shape
    backend/bot-bridge/stage-roster.mjs       names drawn on the stage
    packages/apps/GrokHome/src/dev/grokhome/BotIdentity.java   NAMES / SHAPES / COLORS arrays
    packages/apps/GrokHome/src/dev/grokhome/DemoMode.java      demo lists, dropped names replaced
    app/build.gradle                          version (only with --bump)

It never contacts a bot, never touches saved tablet preferences, and never deletes a real bot.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOTS_JSON = ROOT / "backend/bot-bridge/bots.local.json"
STAGE_MJS = ROOT / "backend/bot-bridge/stage-roster.mjs"
IDENTITY = ROOT / "packages/apps/GrokHome/src/dev/grokhome/BotIdentity.java"
BALL_COLORS = ROOT / "packages/apps/GrokHome/src/dev/grokhome/BallColors.java"
DEMO = ROOT / "packages/apps/GrokHome/src/dev/grokhome/DemoMode.java"
GRADLE = ROOT / "app/build.gradle"
SHAPES = ["sphere", "star", "heart", "capsule", "cloud", "flower", "hexagon", "square", "triangle"]
FILL_QUESTIONS = ["Keep going with this?", "Ship it now?", "Is this the right version?", "Pick one for me?"]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def palette():
    """(name -> 'rrggbb') read from BallColors.java so the script follows the app's real palette."""
    src = read(BALL_COLORS)
    names = re.search(r"NAMES=\{([^}]*)\}", src).group(1)
    names = [n.strip().strip('"') for n in names.split(",")]
    values = re.search(r"VALUES=\{([^}]*)\}", src, re.S).group(1)
    consts = {"WHITE": "ffffff", "RED": re.search(r"RED=0xff([0-9a-f]{6})", src).group(1)}
    out = []
    for token in re.split(r"[,\s]+", values.strip()):
        if not token:
            continue
        out.append(consts[token] if token in consts else re.fullmatch(r"0xff([0-9a-f]{6})", token).group(1))
    if len(out) != len(names):
        sys.exit("BallColors.java: NAMES and VALUES do not line up; the palette parser needs updating.")
    return dict(zip([n.lower() for n in names], out))


def color_hex(value: str, pal) -> str:
    v = value.strip().lower().lstrip("#")
    if v in pal:
        return pal[v]
    if re.fullmatch(r"[0-9a-f]{6}", v) and v in pal.values():
        return v
    sys.exit(f"Color {value!r} is not in the app palette. Use one of: {', '.join(pal)}")


def java_color(hexv: str) -> str:
    if hexv == "ffffff":
        return "BallColors.WHITE"
    red = re.search(r"RED=0xff([0-9a-f]{6})", read(BALL_COLORS)).group(1)
    if hexv == red:
        return "BallColors.RED"
    return "0xff" + hexv


def js_string(name: str) -> str:
    out = []
    for ch in name:
        if ch == "'" or ch == "\\":
            out.append("\\" + ch)
        elif ord(ch) < 128:
            out.append(ch)
        else:
            out.append("\\u{%X}" % ord(ch))
    return "'" + "".join(out) + "'"


def java_string(name: str) -> str:
    return '"' + name.replace("\\", "\\\\").replace('"', '\\"') + '"'


BOTS_EXAMPLE = ROOT / "backend/bot-bridge/bots.example.json"


def load_current():
    """The roster in bots.local.json; a fresh checkout has only bots.example.json, so start from that."""
    source = BOTS_JSON if BOTS_JSON.exists() else BOTS_EXAMPLE
    return [{"name": b["name"], "color": b["color"].lstrip("#").lower(), "shape": b.get("shape", "sphere")}
            for b in json.loads(read(source))]


def plan(spec, current, pal):
    by_name = {b["name"]: b for b in current}
    seen = set()
    result, problems = [], []
    for i, item in enumerate(spec):
        name = item.get("name", "")
        if not name.strip() or name != name.strip():
            problems.append(f"entry {i + 1}: name must be exact, without leading or trailing spaces: {name!r}")
            continue
        if name.lower() in seen:
            problems.append(f"duplicate name (case-insensitive): {name}")
            continue
        seen.add(name.lower())
        old = by_name.get(name)
        if old is None:
            close = [n for n in by_name if n.lower() == name.lower()]
            if close:
                problems.append(f"{name!r} differs only in case from existing {close[0]!r}; use the exact name")
                continue
        color = item.get("color")
        shape = item.get("shape")
        if color is None:
            if old is None:
                problems.append(f"new bot {name!r} needs a color")
                continue
            color = old["color"]
        if shape is None:
            if old is None:
                problems.append(f"new bot {name!r} needs a shape")
                continue
            shape = old["shape"]
        if shape not in SHAPES:
            problems.append(f"{name}: shape {shape!r} is not supported. Use one of: {', '.join(SHAPES)}")
            continue
        try:
            hexv = color_hex(color, pal)
        except SystemExit as e:
            problems.append(f"{name}: {e}")
            continue
        result.append({"name": name, "color": hexv, "shape": shape})
    if not result and not problems:
        problems.append("the roster would be empty; the app needs at least one bot")
    return result, problems


def demo_lists(src: str, names):
    """Drop names that are not in the roster from DemoMode's lists, then top up lists that ran short."""
    lists = {}
    for key in ("WORKING", "CELEBRATING", "HANDS", "QUESTIONS", "UNREAD"):
        m = re.search(r"static final String\[\] %s = \{([^}]*)\};" % key, src)
        if not m:
            sys.exit(f"DemoMode.java: could not find {key}; update sync_roster.py")
        lists[key] = [s for s in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))]
    questions = list(lists["QUESTIONS"])
    hands = list(lists["HANDS"])
    keep_hands, keep_questions = [], []
    for i, h in enumerate(hands):
        if h in names:
            keep_hands.append(h)
            keep_questions.append(questions[i] if i < len(questions) else FILL_QUESTIONS[0])
    lists["HANDS"], lists["QUESTIONS"] = keep_hands, keep_questions
    for key in ("WORKING", "CELEBRATING", "UNREAD"):
        lists[key] = [n for n in lists[key] if n in names]
    others = [n for n in names if n != names[0]]
    want = {"WORKING": min(3, len(others)), "CELEBRATING": min(2, len(others)), "HANDS": min(3, len(others))}
    used = set(lists["WORKING"]) | set(lists["CELEBRATING"]) | set(lists["HANDS"])
    free = [n for n in others if n not in used]
    for key in ("WORKING", "CELEBRATING", "HANDS"):
        while len(lists[key]) < want[key] and free:
            pick = free.pop(0)
            lists[key].append(pick)
            if key == "HANDS":
                lists["QUESTIONS"].append(FILL_QUESTIONS[len(lists["QUESTIONS"]) % len(FILL_QUESTIONS)])
    if len(lists["UNREAD"]) < min(5, len(others)):
        for n in others:
            if n not in lists["UNREAD"] and len(lists["UNREAD"]) < min(5, len(others)):
                lists["UNREAD"].append(n)
    return lists


def replace_array(src: str, key: str, values):
    pattern = re.compile(r"(static final String\[\] %s = )\{[^}]*\};" % key)
    text = ", ".join(java_string(v) for v in values)
    new, n = pattern.subn(lambda m: m.group(1) + "{" + text + "};", src, count=1)
    if n != 1:
        sys.exit(f"DemoMode.java: could not rewrite {key}")
    return new


def bump(gradle_src: str):
    m = re.search(r"versionCode (\d+); versionName '(\d+)\.(\d+)\.(\d+)[^']*'", gradle_src)
    if not m:
        sys.exit("app/build.gradle: versionCode/versionName pattern not found")
    code, major, minor, patch = (int(x) for x in m.groups())
    name = f"{major}.{minor}.{patch + 1}-roster"
    new = gradle_src[:m.start()] + f"versionCode {code + 1}; versionName '{name}'" + gradle_src[m.end():]
    return new, code + 1, name


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("spec")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--bump", action="store_true", help="raise versionCode and the patch number in app/build.gradle")
    args = ap.parse_args()

    pal = palette()
    spec = json.loads(Path(args.spec).read_text(encoding="utf-8"))
    current = load_current()
    roster, problems = plan(spec, current, pal)
    if problems:
        print("Cannot continue:")
        for p in problems:
            print("  -", p)
        sys.exit(2)

    cur = {b["name"]: b for b in current}
    new = {b["name"]: b for b in roster}
    added = [n for n in new if n not in cur]
    removed = [n for n in cur if n not in new]
    changed = [n for n in new if n in cur and (new[n]["color"] != cur[n]["color"] or new[n]["shape"] != cur[n]["shape"])]
    reordered = [b["name"] for b in current if b["name"] in new] != [b["name"] for b in roster if b["name"] in cur]

    print(f"Roster after sync: {len(roster)} bots")
    for b in roster:
        tag = "NEW " if b["name"] in added else "    "
        print(f"  {tag}{b['name']:<16} #{b['color']}  {b['shape']}")
    print("Added:   ", added or "none")
    print("Removed: ", removed or "none")
    print("Changed: ", changed or "none")
    print("Reordered:", reordered)
    if roster:
        print(f"Primary bot (first in the list): {roster[0]['name']}. It gets the crown, the big ambient tile and the voice shortcuts.")
        if current and current[0]["name"] != roster[0]["name"]:
            print(f"NOTE: the primary bot changes from {current[0]['name']} to {roster[0]['name']}.")

    names = [b["name"] for b in roster]
    demo_src = read(DEMO)
    lists = demo_lists(demo_src, names)
    new_demo = demo_src
    for key in ("WORKING", "CELEBRATING", "HANDS", "QUESTIONS", "UNREAD"):
        new_demo = replace_array(new_demo, key, lists[key])

    identity = (
        "package dev.grokhome;\n\n"
        "/** Upright identities from the configured roster; animation supplies only natural motion. */\n"
        "final class BotIdentity {\n"
        "    static final String[] NAMES={" + ",".join(java_string(n) for n in names) + "};\n"
        "    static final String[] SHAPES={" + ",".join(java_string(b["shape"]) for b in roster) + "};\n"
        "    static final int[] COLORS={" + ",".join(java_color(b["color"]) for b in roster) + "};\n"
        "    /** The coordinator: first in the roster. It gets the crown, the big wall tile and the voice shortcuts. */\n"
        "    static final String PRIMARY=NAMES[0];\n"
        "    static int index(String name){for(int i=0;i<NAMES.length;i++)if(NAMES[i].equals(name))return i;return -1;}\n"
        "    static String shape(String name){int i=index(name);return i<0?\"sphere\":SHAPES[i];}\n"
        "    static int color(String name){int i=index(name);return i<0?BallColors.WHITE:COLORS[i];}\n"
        "    private BotIdentity(){}\n"
        "}\n"
    )
    bots_json = "[\n" + ",\n".join(
        '  { "name": %s, "color": "#%s", "shape": "%s" }' % (json.dumps(b["name"], ensure_ascii=False), b["color"].upper(), b["shape"])
        for b in roster) + "\n]\n"
    stage = ("/** Names drawn on the stage. This list never contacts a bot. */\n"
             "export const STAGE_ROSTER = [" + ", ".join(js_string(n) for n in names) + "];\n")

    gradle_note = ""
    new_gradle = None
    if args.bump:
        new_gradle, code, vname = bump(read(GRADLE))
        gradle_note = f"version -> {code} / {vname}"

    stale = []
    if removed:
        needles = removed
        for sub in ("app/src", "packages/apps/GrokHome/src", "backend"):
            for path in (ROOT / sub).rglob("*"):
                if path.suffix not in (".java", ".mjs", ".py", ".ts", ".json", ".md", ".txt") or "node_modules" in path.parts \
                        or ".agent-serve" in path.parts or path.name in ("bots.local.json",) or ".bak" in path.name:
                    continue
                try:
                    text = path.read_text(encoding="utf-8")
                except (UnicodeDecodeError, OSError):
                    continue
                for n in needles:
                    if re.search(r"\b%s\b" % re.escape(n), text):
                        stale.append((str(path.relative_to(ROOT)), n))
    if stale:
        in_tests = sorted({p for p, _ in stale if "/src/test/" in p or "/src/androidTest/" in p})
        elsewhere = sorted({(p, n) for p, n in stale if p not in in_tests})
        if elsewhere:
            print("\nRemoved names still mentioned outside tests (check these):")
            for p, n in elsewhere:
                print(f"  {p}: {n}")
        if in_tests:
            print(f"\n{len(in_tests)} test file(s) mention removed names. They assert against the old roster, so some Android unit tests")
            print("will fail until you point them at bots that still exist (prefer BotIdentity.NAMES[...]). That is expected.")

    if not args.apply:
        print("\nDry run only. Re-run with --apply to write the files." + (f" ({gradle_note})" if gradle_note else ""))
        return
    BOTS_JSON.write_text(bots_json, encoding="utf-8", newline="\n")
    STAGE_MJS.write_text(stage, encoding="utf-8", newline="\n")
    IDENTITY.write_text(identity, encoding="utf-8", newline="\n")
    DEMO.write_text(new_demo, encoding="utf-8", newline="\n")
    if new_gradle:
        GRADLE.write_text(new_gradle, encoding="utf-8", newline="\n")
    print("\nWritten: bots.local.json, stage-roster.mjs, BotIdentity.java, DemoMode.java" + (f", build.gradle ({gradle_note})" if gradle_note else ""))
    print("Next: scripts/roster/ship.sh (tests + APK), then the install steps in the skill.")


if __name__ == "__main__":
    main()
