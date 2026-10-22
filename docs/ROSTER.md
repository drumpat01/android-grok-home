# Your bots: add, remove, reorder

The roster lives in four places that must agree. `scripts/roster/sync_roster.py` writes all four from one list, so you never edit them by hand.

| File | Role |
|---|---|
| `backend/bot-bridge/bots.local.json` | the allowlist the bridge uses to talk to your real bots (git-ignored) |
| `packages/apps/GrokHome/src/dev/grokhome/BotIdentity.java` | names, shapes and colors the app draws |
| `backend/bot-bridge/stage-roster.mjs` | names drawn on the stage |
| `packages/apps/GrokHome/src/dev/grokhome/DemoMode.java` | which bots Demo mode animates |

`scripts/doctor.py` reports when they disagree.

## The spec

A JSON list of the whole roster you want, in display order. Bots missing from the list are removed; new ones are added.

```json
[
  {"name": "Max"},
  {"name": "Researcher", "color": "teal",   "shape": "capsule"},
  {"name": "Ops Bot",    "color": "purple", "shape": "hexagon"}
]
```

- **name**: exact, as your account shows it. Case and spaces matter. Never guess: an unknown name may cause the upstream service to create an empty bot when it is first messaged.
- **color**: required for a new bot, optional for an existing one (it keeps what it has). A palette name or its hex value.

  | white `#FFFFFF` | brown `#A16B39` | red `#FC2C3E` | orange `#F9670E` | amber `#F69412` | green `#14C370` |
  |---|---|---|---|---|---|
  | teal `#13B8A3` | blue `#1884FC` | purple `#905AFB` | pink `#FB3499` | gray `#777777` | |

- **shape**: required for a new bot. One of `sphere`, `star`, `heart`, `capsule`, `cloud`, `flower`, `hexagon`, `square`, `triangle`. A sphere renders as a true ball.
- The **first** bot gets the big tile on the ambient wall and the optional coordinator features.

## Run it

```sh
python3 scripts/roster/sync_roster.py my-bots.json                 # dry run: shows added, removed, changed; writes nothing
python3 scripts/roster/sync_roster.py my-bots.json --apply         # write the files
python3 scripts/roster/sync_roster.py my-bots.json --apply --bump  # also raise versionCode and the patch version
```

It stops with a list of problems (duplicate names, off-palette colors, unknown shapes, an empty roster) and writes nothing if any check fails. It never contacts a bot and never touches saved preferences on the device.

A fresh checkout starts from `bots.example.json`. After the first `--apply` your roster is in `bots.local.json`.

## Then ship it

```sh
bash scripts/roster/ship.sh          # tests (see note), APK build, graceful bridge restart so the allowlist updates
```

On Windows, `scripts/roster/ship.ps1 [-Serial <adb address>]` then copies the APK, publishes it for Settings > Update app, and installs it if you pass a device. Elsewhere, `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

`ship.sh` runs the Android unit tests, which assert against the **example** roster. After you replace the roster some wall, carousel and needs-you tests fail by design. Use `--skip-tests` to build without them.

## From a screenshot

If the person sends a screenshot of their bot list, read the names exactly as shown, give new bots a nearby palette color and shape, keep existing bots as `{"name": ...}`, and tell them the looks are easy to change. Do not reorder to match the screenshot's grouping unless asked.

## Removing a bot

Leaving it out of the spec removes it from the bridge allowlist, the app and the stage. It does **not** delete the real bot, its chats or its routines, and the device keeps that bot's saved color, voice and history so adding it back restores them.
