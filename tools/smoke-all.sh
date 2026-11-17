#!/usr/bin/env bash
# Starts every version until the title screen (the mod quits by itself there with -Dvibeylyrics.smoke=true)
# and reports which reached it and whether the mixins fit. Usage: tools/smoke-all.sh [versions...]
cd "$(dirname "$0")/.." || exit 1
LOGS=build/smoke
mkdir -p "$LOGS"
VERSIONS=("$@")
[ ${#VERSIONS[@]} -eq 0 ] && VERSIONS=(1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1 26.2 26.3)
for v in "${VERSIONS[@]}"; do
  log="$LOGS/fabric-$v.log"
  timeout 900 ./gradlew ":fabric-$v:runSmoke" --console=plain > "$log" 2>&1
  if grep -q "mixin FAILED\|MixinApplyError\|InvalidInjectionException" "$log"; then
    echo "fabric-$v: MIXIN FAILED (see $log)"
  elif grep -q "\[vibeylyrics-smoke\] title screen reached" "$log"; then
    echo "fabric-$v: OK ($(grep -o '[0-9]* songs in the library, [0-9]* mixin targets loaded' "$log" | head -1))"
  else
    echo "fabric-$v: FAILED (see $log)"
  fi
done
