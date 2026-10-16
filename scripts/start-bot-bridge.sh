#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="$project_root/.tools/node/bin:$PATH"
cd "$project_root/backend/bot-bridge"
if ! node --input-type=module -e 'import {configuredBots} from "./config.mjs"; if(!configuredBots().length) {console.error("Add the exact existing bot names to backend/bot-bridge/bots.local.json first."); process.exit(1);}' ; then
  exit 1
fi
if ! ./node_modules/.bin/bdk whoami --json >/dev/null 2>&1; then
  echo 'Sign in first with: ./node_modules/.bin/bdk login'
  exit 1
fi
exec ./node_modules/.bin/bdk serve --dir . --mode single --host 127.0.0.1 --port 8790 --no-schedules --no-playground --no-docs
