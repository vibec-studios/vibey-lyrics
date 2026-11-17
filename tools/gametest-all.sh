#!/usr/bin/env bash
# Runs the client game test (world + demo song + screenshots) on the given versions and reports whether the world
# render hook drew. Usage: tools/gametest-all.sh [versions...]   (default: one per render path)
cd "$(dirname "$0")/.." || exit 1
LOGS=build/gametest
mkdir -p "$LOGS"
VERSIONS=("$@")
[ ${#VERSIONS[@]} -eq 0 ] && VERSIONS=(1.21.4 1.21.8 1.21.10 1.21.11 26.1 26.2 26.3)
for v in "${VERSIONS[@]}"; do
  log="$LOGS/fabric-$v.log"
  timeout 1500 ./gradlew ":fabric-$v:runClientGameTest" --console=plain > "$log" 2>&1
  code=$?
  drew=$(grep -o "world hook drew [0-9]* frames" "$log" | head -1)
  if [ $code -eq 0 ] && [ -n "$drew" ]; then echo "fabric-$v: OK ($drew)"; else echo "fabric-$v: FAILED (exit $code, see $log)"; fi
done
