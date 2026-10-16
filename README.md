# Android Grok Home

An unofficial native Android home launcher with animated bot avatars, voice and text conversations, and a computer-side bridge to existing Grok Bots through Cursor BDK.

This is a standalone APK. Installing it does not flash firmware. Android 11 or later is required. This project is independent of xAI, Cursor, Samsung, and Google.

## Features

- Animated avatars and a swipeable bot carousel, with stage appearance controls.
- Per-bot chat history, drafts, unread indicators, and foreground activity summaries.
- Android speech recognition and text-to-speech, task progress, and cancellation.
- Ambient mode and optional private remote access through Tailscale.
- A private APK update server.

## Build

Install JDK 17 and an Android SDK with platform 34 and build tools. Set `ANDROID_HOME`, or create an ignored `local.properties` containing `sdk.dir=/absolute/path/to/android-sdk`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Instrumentation tests require a connected emulator or Android device. Debug builds are for development; keep your own signing key private if distributing release builds.

## Configure existing bots

Install Node.js 22.13 or later and Python 3.10 or later. From the repository root:

```sh
cd backend/bot-bridge
npm ci
cp bots.example.json bots.local.json
```

Edit the ignored `bots.local.json` with the exact names of bots you already own. Verify names in your account before sending: an unknown name may cause the upstream service to create a bot. The example config uses Max; only keep that name if your account already owns that bot. The names and colors in `BotIdentity.java` are illustrative display presets. Max is the coordinator name used by the optional routing and voice-wake features; configure an existing bot named Max to use those features.

```sh
npx bdk login
cd ../..
bash scripts/start-bot-bridge.sh
python3 backend/server.py --enable-bots --disable-xai
```

The bridge listens on loopback port 8790; the gateway defaults to loopback port 8787. For an emulator or a USB-connected device, use `adb reverse tcp:8787 tcp:8787`. Open the app settings and set the computer connection address for your environment. The default address is loopback and does not point to someone else's computer.

See [connection setup](docs/SETUP.md) for Windows, LAN, Tailscale and optional xAI voice instructions, and [bridge details](backend/bot-bridge/README.md).

## Tests

```sh
python3 -m unittest discover -s backend -v
cd backend/bot-bridge
npm test
```

These commands use synthetic fixtures and do not send messages to live bots. Android instrumentation fixtures use example bot names. Live microphone quality and account-specific routing require your own device checks.

## Security and limitations

Keep BDK port 8790 on loopback. LAN mode trusts the configured private network; use it only on a trusted network. For remote access, use private Tailscale Serve with restrictive tailnet access policies. Do not expose the gateway or BDK with public port forwarding or Tailscale Funnel.

The native activity reader uses undocumented Cursor desktop APIs and internal BDK imports. It may break when upstream interfaces change. The adapter is isolated from the regular send/check path. Users supply their own account access; no provider credentials are included.

Local credentials, bot configuration, build outputs, diagnostic dumps, and signing keys are ignored by Git. Never upload real conversations, login state, or private device captures. Patched transitive dependencies are pinned through npm overrides; see [SECURITY.md](SECURITY.md) for the current audit status and OAuth upgrade notes.

## License

MIT; see [LICENSE](LICENSE). Third-party dependencies retain their own licenses. Grok and other product names belong to their respective owners.
