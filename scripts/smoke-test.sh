#!/usr/bin/env bash
# Starts a real Paper server with the plugin, waits until it is up, stops it again
# and fails if PorticleGun did not enable cleanly or logged errors.
#
# usage: scripts/smoke-test.sh <plugin.jar> [minecraft-version]
set -euo pipefail

PLUGIN_JAR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
MC_VERSION="${2:-1.21.4}"
STARTUP_TIMEOUT=300
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/run/smoke-$MC_VERSION"
CACHE="$ROOT/run/cache"

mkdir -p "$CACHE"
rm -rf "$WORK"
mkdir -p "$WORK/plugins"

# download Paper (cached by checksum)
BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest")"
read -r PAPER_URL PAPER_SHA <<<"$(python3 -c '
import json, sys
d = json.loads(sys.argv[1])["downloads"]["server:default"]
print(d["url"], d["checksums"]["sha256"])' "$BUILD_JSON")"
PAPER_JAR="$CACHE/paper-$PAPER_SHA.jar"
if [ ! -f "$PAPER_JAR" ]; then
    echo "Downloading $PAPER_URL"
    curl -fsSL -o "$PAPER_JAR.part" "$PAPER_URL"
    echo "$PAPER_SHA  $PAPER_JAR.part" | shasum -a 256 -c - >/dev/null
    mv "$PAPER_JAR.part" "$PAPER_JAR"
fi

cp "$PLUGIN_JAR" "$WORK/plugins/"
echo "eula=true" >"$WORK/eula.txt"
cat >"$WORK/server.properties" <<PROPS
online-mode=false
server-port=25599
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
PROPS

LOG="$WORK/server.log"
: >"$LOG"
cd "$WORK"

# feed "stop" to the console once the server is up (or the timeout is reached)
(
    for _ in $(seq "$STARTUP_TIMEOUT"); do
        grep -q "Done (" "$LOG" 2>/dev/null && break
        sleep 1
    done
    sleep 3
    # exercise the command dispatcher and the reload path from the console
    echo "porticlegun list"
    sleep 1
    echo "porticlegun reload"
    sleep 2
    echo "stop"
) | java -Xms1G -Xmx2G -jar "$PAPER_JAR" --nogui >"$LOG" 2>&1 || true

fail() {
    echo "SMOKE TEST FAILED: $1"
    echo "----- server.log (PorticleGun / errors) -----"
    grep -n -i -E "porticlegun|exception|error" "$LOG" | head -100 || true
    exit 1
}

grep -q "Done (" "$LOG" || fail "server did not finish starting within ${STARTUP_TIMEOUT}s"
grep -q "\[PorticleGun\] PorticleGun has been enabled" "$LOG" || fail "PorticleGun was not enabled"
grep -q "Disabling PorticleGun" "$LOG" || fail "PorticleGun was not disabled on shutdown"
grep -q "No stored portal guns found" "$LOG" || fail "/porticlegun list did not answer"
grep -q "PorticleGun data reloaded from disk" "$LOG" || fail "/porticlegun reload did not succeed"
if grep -n -E "Error occurred while (enabling|disabling) PorticleGun|Could not load 'plugins/PorticleGun" "$LOG"; then
    fail "PorticleGun failed to load, enable or disable"
fi
if grep -n -B2 -A8 -E "^\s+at eu\.nurkert\.porticlegun\." "$LOG"; then
    fail "an exception was thrown from PorticleGun code"
fi

echo "Smoke test passed on Paper $MC_VERSION"
