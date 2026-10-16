# Connection setup

## Windows and Ubuntu WSL

The PowerShell helpers assume Windows with the Ubuntu WSL distribution, an Android SDK in `%LOCALAPPDATA%/Android/Sdk`, Node.js installed in WSL, and Python available in Windows. Set `GROK_HOME_PYTHON` to the full Windows Python executable path if it is not on PATH. Run scripts from the checkout; paths are resolved from their location.

Keep personal configuration in environment variables or an ignored `*.local.ps1` file that sets them before invoking a helper. Never commit those values.

For home Wi-Fi, set `GROK_HOME_ADDRESS` to the computer's LAN IPv4 and `GROK_HOME_SUBNET` to its private CIDR. Example values are `192.168.1.10` and `192.168.1.0/24`; replace both with your network. Run `scripts/configure-wifi-firewall.ps1` as administrator, then `scripts/start-home-wifi.ps1`. The named firewall rule allows only the configured subnet; if network settings change, remove the old named rule and rerun configuration. Set the app's computer connection to `http://YOUR_LAN_IP:8788`.

The firewall helper requires a private network. Do not run it on an untrusted or shared network. The backend validates the bind address and CIDR before serving.

## Android device selection

Set `GROK_HOME_TABLET_SERIAL` to your device's hardware serial, obtained locally from `adb shell getprop ro.serialno`. Pair wireless debugging through Android's settings first if using Wi-Fi ADB. `scripts/connect-tablet.ps1` checks the serial before selecting a device; no serial is shipped in this repository.

Build the debug APK, then run `scripts/install-bots-tablet.ps1`. Installation replaces the app on the selected device. The helper never flashes firmware.

## Private remote access

Install Tailscale and sign in on the computer and Android device. Configure tailnet access so only your trusted devices can reach the gateway. Run `scripts/start-tailscale.ps1`; it derives your private hostname from Tailscale and creates a private Serve listener on port 9443. It does not enable Funnel. Set the app connection address to the HTTPS URL printed by the script.

The optional `scripts/watch-home-bridge.ps1` monitor requires `GROK_HOME_PRIVATE_URL` set to that private URL. If copying the monitor out of the checkout, also set `GROK_HOME_PROJECT` to the checkout path. The monitor is started manually; this repository does not install a startup entry. Keep the computer on while using the bridge.

## Optional xAI realtime voice

Bot mode uses Android speech and does not need an xAI API key. To use xAI realtime voice, copy `backend/.env.example` to `backend/.env` and set your own key there. Start `python3 backend/server.py` without `--disable-xai`. Keys stay on the computer; the app receives short-lived credentials. API use may incur charges on your account.

For emulator use, build the debug APK and run `scripts/start-voice.ps1` with an existing emulator. The default AVD name used when launching an emulator is `GrokHomeOffline`; create that AVD or start your preferred emulator first. The helper forwards loopback port 8787 with ADB.

## APK updates

`scripts/publish-app-update.ps1` copies a built APK into ignored local update storage and generates metadata. This serves updates through your private gateway; it does not publish to GitHub or an app store. Updates must use the same package and signing key as the installed app. Keep signing material outside Git.
