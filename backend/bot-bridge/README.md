# Existing-bot bridge

The bridge uses pinned `@cursor/bdk` 0.2.25 and the signed-in user's own credentials. It forwards explicitly requested messages to a configured existing bot and returns the delivered reply. It does not automatically retry sends.

Run `npm ci`, copy `bots.example.json` to ignored `bots.local.json`, and enter exact bot names from your account. Unknown names can cause upstream bot creation; verify names first. Run `npx bdk login` locally, then `bash scripts/start-bot-bridge.sh` from the repository root.

BDK serves on `127.0.0.1:8790`. Keep that interface private. Login state belongs in the BDK user's configuration directory and must never be committed. Run `npm test` for offline adapter checks.

The native activity reader uses undocumented desktop transcript RPCs and internal BDK credential helpers. It performs allowlisted read operations using the current user's credentials. Its schema and imports can change without notice. The regular send/check bridge remains separate. Real transcripts, bot IDs, session IDs and activity exports must stay local.

A busy bot should be checked rather than sent the same request again. After an ambiguous timeout or gateway restart, inspect the existing bot conversation before resending. Earlier unread replies are separate from the reply to a new request.

See the root README and `docs/SETUP.md` for Android, networking, and gateway setup.
