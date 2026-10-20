#!/usr/bin/env bash
# Starts a real Paper server with the plugin, waits until it is up, stops it again
# and fails if PorticleGun did not enable cleanly or logged errors.
#
# usage: scripts/smoke-test.sh <plugin.jar> [minecraft-version] [geyser]
#   geyser: also install Geyser and Floodgate and check that Bedrock support is enabled
set -euo pipefail

PLUGIN_JAR="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
MC_VERSION="${2:-1.21.4}"
WITH_GEYSER="${3:-}"
STARTUP_TIMEOUT=300
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/run/smoke-$MC_VERSION${WITH_GEYSER:+-$WITH_GEYSER}"
CACHE="$ROOT/run/cache"

mkdir -p "$CACHE"
rm -rf "$WORK"
mkdir -p "$WORK/plugins"

# download Paper (cached per version and checksum; falls back to the cache when the API is unreachable)
PAPER_JAR=""
if BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest")"; then
    read -r PAPER_URL PAPER_SHA <<<"$(python3 -c '
import json, sys
d = json.loads(sys.argv[1])["downloads"]["server:default"]
print(d["url"], d["checksums"]["sha256"])' "$BUILD_JSON")"
    PAPER_JAR="$CACHE/paper-$MC_VERSION-$PAPER_SHA.jar"
    if [ ! -f "$PAPER_JAR" ]; then
        echo "Downloading $PAPER_URL"
        curl -fsSL -o "$PAPER_JAR.part" "$PAPER_URL"
        echo "$PAPER_SHA  $PAPER_JAR.part" | shasum -a 256 -c - >/dev/null
        mv "$PAPER_JAR.part" "$PAPER_JAR"
    fi
else
    PAPER_JAR="$(ls -t "$CACHE"/paper-"$MC_VERSION"-*.jar 2>/dev/null | head -n 1 || true)"
    [ -n "$PAPER_JAR" ] || { echo "Paper API unreachable and no cached Paper $MC_VERSION jar"; exit 1; }
    echo "Paper API unreachable, using cached $PAPER_JAR"
fi

cp "$PLUGIN_JAR" "$WORK/plugins/"
if [ "$WITH_GEYSER" = "geyser" ]; then
    for project in geyser floodgate; do
        curl -fsSL -o "$WORK/plugins/$project.jar" \
            "https://download.geysermc.org/v2/projects/$project/versions/latest/builds/latest/downloads/spigot"
    done
fi
echo "eula=true" >"$WORK/eula.txt"
cat >"$WORK/server.properties" <<PROPS
online-mode=false
server-port=25599
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
PROPS

# stored portals: a normal gun, one with a colour that no longer exists, one in a world that is not loaded
mkdir -p "$WORK/plugins/PorticleGun"
cat >"$WORK/plugins/PorticleGun/portals.yml" <<YAML
porticleguns:
  abcdefghij:
    shape: ELLIPTIC
    primary:
      position: world:10.0:64.0:10.0:1.0:0.0:0.0
      color: gold
    secondary:
      position: world:20.0:64.0:10.0:-1.0:0.0:0.0
      color: dark_blue
  klmnopqrst:
    primary:
      position: world:30.0:64.0:10.0:1.0:0.0:0.0
      color: does_not_exist
  uvwxyzABCD:
    primary:
      position: world_not_loaded:0.0:64.0:0.0:0.0:1.0:0.0
YAML

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
if [ "$WITH_GEYSER" = "geyser" ]; then
    grep -q "Floodgate detected - Bedrock support enabled" "$LOG" || fail "Bedrock support was not enabled with Floodgate installed"
    grep -q "Installed the PorticleGun Bedrock pack" "$LOG" || fail "the Bedrock pack was not installed into Geyser"
    [ -f "$WORK/plugins/Geyser-Spigot/packs/porticlegun.mcpack" ] || fail "porticlegun.mcpack missing in Geyser's packs folder"
fi
grep -q "Stored portal guns (3)" "$LOG" || fail "/porticlegun list did not show the 3 stored guns"
grep -q "Unknown portal colour 'does_not_exist'" "$LOG" || fail "unknown colour was not reported"
grep -E -q "primary: .*active.*secondary: .*active" "$LOG" || fail "the stored portal pair was not activated"
grep -E -q "primary: .*saved" "$LOG" || fail "the portal in the unloaded world should stay saved but inactive"
for gun in abcdefghij klmnopqrst uvwxyzABCD; do
    grep -q "$gun:" "$WORK/plugins/PorticleGun/portals.yml" || fail "gun $gun was lost from portals.yml"
done
grep -q "PorticleGun data reloaded from disk" "$LOG" || fail "/porticlegun reload did not succeed"
if grep -n -E "Error occurred while (enabling|disabling) PorticleGun|Could not load 'plugins/PorticleGun" "$LOG"; then
    fail "PorticleGun failed to load, enable or disable"
fi
if grep -n -B2 -A8 -E "^\s+at eu\.nurkert\.porticlegun\." "$LOG"; then
    fail "an exception was thrown from PorticleGun code"
fi

echo "Smoke test passed on Paper $MC_VERSION${WITH_GEYSER:+ with Geyser and Floodgate}"
