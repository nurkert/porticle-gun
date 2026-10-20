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

BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest")"
read -r PAPER_URL PAPER_SHA <<<"$(python3 -c '
import json, sys
d = json.loads(sys.argv[1])["downloads"]["server:default"]
print(d["url"], d["checksums"]["sha256"])' "$BUILD_JSON")"
PAPER_JAR="$CACHE/paper-$PAPER_SHA.jar"
[ -f "$PAPER_JAR" ] || curl -fsSL -o "$PAPER_JAR" "$PAPER_URL"

for project in geyser floodgate; do
    [ -f "$WORK/plugins/$project.jar" ] || curl -fsSL -o "$WORK/plugins/$project.jar" \
        "https://download.geysermc.org/v2/projects/$project/versions/latest/builds/latest/downloads/spigot"
done

echo "eula=true" >"$WORK/eula.txt"
[ -f "$WORK/server.properties" ] || printf 'online-mode=false\nspawn-protection=0\n' >"$WORK/server.properties"

cd "$WORK"
exec java -Xms1G -Xmx2G -jar "$PAPER_JAR" --nogui
