#!/usr/bin/env bash
# Starts every version until the title screen (the mod quits by itself there with -Dvibeylyrics.smoke=true)
# and reports which reached it. Usage: tools/smoke-all.sh [log-dir]
cd "$(dirname "$0")/.." || exit 1
LOGS="${1:-build/smoke}"
mkdir -p "$LOGS"
for v in 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1 26.2 26.3; do
  log="$LOGS/fabric-$v.log"
  timeout 900 ./gradlew ":fabric-$v:runSmoke" --console=plain > "$log" 2>&1
  if grep -q "\[vibeylyrics-smoke\] title screen reached" "$log"; then
    echo "fabric-$v: OK ($(grep -o '[0-9]* songs in the library' "$log" | head -1))"
  else
    echo "fabric-$v: FAILED (see $log)"
  fi
done
