#!/usr/bin/env bash
# After sync_roster.py --apply: test, build the APK and reload this project's bot bridge.
# Run inside Ubuntu WSL from anywhere:  bash scripts/roster/ship.sh [--no-bridge] [--skip-tests]
# Long steps log to ~/roster-*.log. Exit code is non-zero if a step fails; nothing is installed or published here.
set -uo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$ROOT/.tools/node/bin:/usr/local/bin:/usr/bin:/bin"
cd "$ROOT" || exit 1
reload_bridge=1; run_tests=1
for a in "$@"; do
  case "$a" in
    --no-bridge) reload_bridge=0 ;;
    --skip-tests) run_tests=0 ;;
  esac
done

step() { echo; echo "== $*"; }

if [ "$run_tests" = 1 ]; then
  step "bridge roster test (stage roster must equal bots.local.json)"
  (cd backend/bot-bridge && node --test stage-roster.test.mjs) || { echo "FAILED: stage roster test"; exit 1; }
  step "Android unit tests (log: ~/roster-tests.log)"
  ./gradlew --no-daemon testDebugUnitTest > ~/roster-tests.log 2>&1
  rc=$?
  grep -E "FAILED|tests completed" ~/roster-tests.log | head -40
  if [ $rc -ne 0 ]; then
    echo "FAILED: unit tests. Names removed from the roster that tests still hard-code are the usual cause (see sync_roster.py output)."
    exit 1
  fi
  echo "unit tests passed"
fi

step "Build debug APK (log: ~/roster-build.log)"
./gradlew --no-daemon assembleDebug > ~/roster-build.log 2>&1 || { tail -30 ~/roster-build.log; echo "FAILED: build"; exit 1; }
ls -la app/build/outputs/apk/debug/app-debug.apk

if [ "$reload_bridge" = 1 ]; then
  step "Reload this project's BDK bridge so its startup allowlist matches the roster"
  LOG=$HOME/bdk-serve.log
  OLD=$(pgrep -f "bot-bridge/node_modules/.bin/bdk serve" | head -1)
  echo "old bridge pid: ${OLD:-none}"
  if [ -n "$OLD" ]; then
    kill -TERM "$OLD"
    for i in $(seq 1 45); do
      kill -0 "$OLD" 2>/dev/null || { echo "old bridge stopped after ${i}s"; break; }
      sleep 1
    done
    if kill -0 "$OLD" 2>/dev/null; then echo "OLD BRIDGE STILL RUNNING after 45s (not forcing it)"; exit 2; fi
  fi
  [ -x backend/bot-bridge/node_modules/.bin/bdk ] || { echo "bot-bridge/node_modules is missing; run 'npm ci' in backend/bot-bridge first"; exit 3; }
  setsid nohup bash scripts/start-bot-bridge.sh > "$LOG" 2>&1 < /dev/null &
  disown
  for i in $(seq 1 40); do
    if curl -s -m 2 http://127.0.0.1:8790/v1/tools >/dev/null 2>&1; then echo "new bridge answering after ${i}s"; break; fi
    sleep 1
  done
  tail -5 "$LOG"
fi
echo
echo "READY: APK built. Next: scripts/roster/ship.ps1 (publish + optional install)."
