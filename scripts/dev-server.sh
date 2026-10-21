#!/usr/bin/env bash
# Starts a local Paper server with the freshly built plugin, Geyser and Floodgate for manual testing.
# Java clients connect to localhost:25565, Bedrock clients to localhost:19132.
# The world is kept in run/dev between runs; type "stop" in the console to quit.
#
# usage: scripts/dev-server.sh [minecraft-version]
set -euo pipefail

MC_VERSION="${1:-1.21.4}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/run/dev"
CACHE="$ROOT/run/cache"
mkdir -p "$CACHE" "$WORK/plugins"

(cd "$ROOT" && mvn -B -q package -DskipTests)
rm -f "$WORK"/plugins/PorticleGun-*.jar
cp "$ROOT"/target/PorticleGun-*.jar "$WORK/plugins/"

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

for project in geyser floodgate; do
    [ -f "$WORK/plugins/$project.jar" ] || curl -fsSL -o "$WORK/plugins/$project.jar" \
        "https://download.geysermc.org/v2/projects/$project/versions/latest/builds/latest/downloads/spigot"
done

echo "eula=true" >"$WORK/eula.txt"
[ -f "$WORK/server.properties" ] || printf 'online-mode=false\nspawn-protection=0\n' >"$WORK/server.properties"

cd "$WORK"
# a moderate heap: a bigger one only gets swapped out on a desktop that also runs the game client
exec java -Xms2G -Xmx3G -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled -XX:+DisableExplicitGC \
    -jar "$PAPER_JAR" --nogui
