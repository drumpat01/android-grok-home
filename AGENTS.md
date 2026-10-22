# Grok Home: setup guide for AI coding agents

You are setting up Grok Home for a person. This file is the contract: what to run, what to expect, what to ask the person, and what you must never do. Follow it top to bottom. Every command is run from the repository root unless it says otherwise.

Grok Home is an Android launcher/app with animated bot avatars, voice and text chat, and a bridge on the person's computer that talks to **bots they already own** through Cursor BDK. Nothing here creates bots, and nothing ships anyone's credentials.

## 0. Rules you must not break

1. **Never message a bot to "test" something** unless the person asked. Do not call `grokbot__ask` or `client.mjs ask` as an existence check: an unknown name can make the upstream service create an empty bot. Verify names from the person (or their screenshot), not by probing.
2. **Bot names are exact.** Case, spaces and emoji all count. Copy them exactly from what the person gives you.
3. **No secrets in git.** Credentials, `bots.local.json`, `.env`, signing keys, device serials, LAN IPs and tailnet names stay out of commits. They are already in `.gitignore`; do not force-add them.
4. **Keep ports private.** BDK listens on loopback 8790 only. Never expose the gateway or BDK to the public internet (no port forwarding, no Tailscale Funnel).
5. **Do not flash, wipe or uninstall app data** on a device. Installing the APK replaces the app only.
6. **Do not sign in for the person.** `bdk login` and Android Wireless debugging pairing need them. Ask, wait, continue.
7. Do not accept terms, create accounts, or enter passwords on their behalf.

## 1. Check the machine

```sh
python3 scripts/doctor.py          # add --json for a machine-readable report
```

Each line is `PASS`, `WARN` or `FAIL` with a `fix:` command. Fix every `FAIL` and rerun until it prints `Ready to build.` Re-run it after each later step; it is cheap and changes nothing.

Prerequisites it checks: Python 3.10+, JDK 17, Android SDK (platform 34 and build-tools; set `ANDROID_HOME` or write `local.properties` with `sdk.dir=/absolute/path`), Node.js 22.13+, adb (only to install on a device), the bridge's `node_modules`, the roster, and whether you are signed in to Cursor.

## 2. Ask the person for these (once, together)

| You need | Why | If they do not know |
|---|---|---|
| The **exact names** of the Grok Bots they own, in the order they want them | the allowlist and the on-screen roster | ask for a screenshot of their bot list and read the names from it |
| Optional: a **color and shape** for each bot | looks only; defaults are fine | pick from the palette in `docs/ROSTER.md` and say they can change it |
| How the device reaches the computer: **emulator / USB**, **home Wi-Fi (LAN)**, or **away via Tailscale** | the address the app uses | start with emulator/USB; it needs no network setup |
| That they will do the **Cursor sign-in** in a browser | `bdk login` is interactive | none |

## 3. Install the bridge and sign in

```sh
cd backend/bot-bridge
npm ci
npx bdk login          # the PERSON completes the browser step; wait for them
npx bdk whoami --json  # must succeed
cd ../..
```

## 4. Configure THEIR bots

Do not edit the Java by hand. Write a spec and let the tool update every place the roster lives (bridge allowlist, app identities, stage roster, demo lists):

```json
[
  {"name": "Alice",   "color": "blue",   "shape": "sphere"},
  {"name": "Build Bot", "color": "orange", "shape": "hexagon"}
]
```

```sh
python3 scripts/roster/sync_roster.py my-bots.json            # dry run, prints the plan and writes nothing
python3 scripts/roster/sync_roster.py my-bots.json --apply    # writes the files
```

The first bot gets the large tile on the ambient wall. Full options, palette, shapes and removing bots: [docs/ROSTER.md](docs/ROSTER.md).

Local-only files this creates or changes: `backend/bot-bridge/bots.local.json` (git-ignored), and tracked `BotIdentity.java`, `stage-roster.mjs`, `DemoMode.java`. Those three are the person's personal roster; do not commit them to the upstream project.

**Expected side effect:** the Android unit tests (`testDebugUnitTest`) assert against the *example* roster, so some wall, carousel and needs-you tests fail once the roster is replaced. That is not a bug in their setup. Verify with `assembleDebug` and `scripts/doctor.py` instead, or run the tests on a clean checkout.

## 5. Build the APK

```sh
./gradlew assembleDebug            # output: app/build/outputs/apk/debug/app-debug.apk
```

Optional wake word ("Hey Bots"): `python3 scripts/fetch-wake-model.py` before building (about 40 MB, checksum-verified). Without it the app works; only the wake word is off.

## 6. Start the services

```sh
bash scripts/start-bot-bridge.sh                          # terminal 1: BDK bridge on 127.0.0.1:8790
python3 backend/server.py --enable-bots --disable-xai     # terminal 2: gateway on 127.0.0.1:8787
```

Run them in the background and keep them running; they stop if the computer reboots. Windows helpers that do the same with firewall and Tailscale handling are listed in [docs/SETUP.md](docs/SETUP.md).

## 7. Install on a device and connect

**Emulator or USB device (simplest):**

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8787 tcp:8787
```

The app's default computer address is `http://127.0.0.1:8787`, which is correct with `adb reverse`.

**Home Wi-Fi:** run the gateway on the computer's LAN address and subnet, then give the app `http://<computer-lan-ip>:8788`:

```sh
python3 backend/server.py --bind <computer-lan-ip> --port 8788 --lan-cidr <lan-cidr, e.g. 192.168.1.0/24> --enable-bots --disable-xai
```

Open the computer's firewall for that port **for the LAN subnet only**. On Windows use `scripts/configure-wifi-firewall.ps1` and `scripts/start-home-wifi.ps1` (see docs/SETUP.md).

**Away from home:** Tailscale on both devices, then `scripts/start-tailscale.ps1` (Windows) or, by hand, run the gateway on `127.0.0.1:8792` with `--tailscale-host <your-machine>.<tailnet>.ts.net` and `tailscale serve --bg --https=9443 http://127.0.0.1:8792`. Give the app `https://<your-machine>.<tailnet>.ts.net:9443`. Never use Funnel.

**Wireless debugging:** the person turns on Developer options > Wireless debugging and gives you the "IP address & Port" (and a pairing code the first time). `adb pair <ip:pairport>` then `adb connect <ip:port>`.

In the app: Settings > Computer connection > Address. The sheet shows live checks (tablet online, Tailscale, gateway answering) with plain-language hints.

## 8. Verify

- `python3 scripts/doctor.py` is all `PASS` (bridge-running and gateway-running included).
- The app's Computer sheet says it is connected and "Last synced" is recent.
- Settings > **Demo mode** shows the whole roster animating with no bots contacted. Use it to confirm the display without sending anything.
- Only if the person agrees: send one short message to one of their bots from the app.

## 9. Later changes

| Task | Do |
|---|---|
| Add or remove bots | edit the spec, `sync_roster.py --apply --bump`, `bash scripts/roster/ship.sh`, then `scripts/roster/ship.ps1` (Windows) or `adb install -r` |
| Update the app on a device | build, then `python3 backend/app_updates.py <apk> --version-code N --version-name X` and use Settings > Update app on the device, or `adb install -r` |
| Change colors | the person can change any bot's color inside the app; shapes need `sync_roster.py` |

## Map of the repository

| Path | What it is |
|---|---|
| `packages/apps/GrokHome/` | the Android app source (Java), resources, manifest |
| `app/` | Gradle module that builds it, plus unit and instrumentation tests |
| `backend/server.py` | the gateway the app talks to (LAN/Tailscale/loopback, update server) |
| `backend/bot_gateway.py`, `activity_gateway.py`, `answer_gateway.py` | bot messaging, activity feed, needs-you answers |
| `backend/bot-bridge/` | Cursor BDK bridge: allowlist, tools, `bots.example.json` |
| `scripts/doctor.py` | read-only environment and configuration check |
| `scripts/roster/` | add/remove bots and ship an APK |
| `scripts/*.ps1`, `scripts/*.sh` | Windows and shell helpers for networking, install and updates |
| `docs/SETUP.md`, `docs/ROSTER.md` | connection setup, roster guide |

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| App says "Can't reach your computer" | gateway not running, wrong address in Settings, or firewall. Run `doctor.py`, check `curl http://127.0.0.1:8787/health`, then the address and port the app uses |
| `start-bot-bridge.sh` exits "Add the exact existing bot names" | `bots.local.json` is missing or empty; do step 4 |
| `start-bot-bridge.sh` exits "Sign in first" | run `npx bdk login` (person does the browser step) |
| `port 8790 is already in use` | a bridge is already running; stop that one gracefully (`kill -TERM <pid>`), do not start a second |
| `adb: device offline` | Wireless debugging was turned off or the device slept; ask the person to switch it on and send the new port |
| Build fails at `sdk.dir` | set `ANDROID_HOME` or create `local.properties` |
| Tests fail on Wall/Carousel/NeedsYou after changing the roster | expected; see step 4 |
| Bot name not accepted / blank replies | the name does not match exactly; ask the person for a screenshot and copy it again |
