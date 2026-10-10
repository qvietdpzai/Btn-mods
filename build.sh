#!/usr/bin/env bash
# Build Btn-mods plugin (Spigot API).
# Downloads spigot-api snapshot if missing, compiles with javac, packages jar.
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"

API_DIR="libs"
API_JAR="$API_DIR/spigot-api.jar"
VER="26.2-R0.1-SNAPSHOT"

mkdir -p "$API_DIR" target/classes

if [[ ! -s "$API_JAR" ]]; then
    echo "[build] Downloading spigot-api $VER ..."
    META="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$VER/maven-metadata.xml"
    # resolve latest snapshot timestamp/build
    XML="$(curl -skL "$META")"
    TS="$(printf '%s' "$XML" | grep -o '<timestamp>[^<]*</timestamp>' | head -1 | sed 's/[^0-9.]//g')"
    BN="$(printf '%s' "$XML" | grep -o '<buildNumber>[^<]*</buildNumber>' | head -1 | sed 's/[^0-9]//g')"
    FVER="${VER%-SNAPSHOT}-${TS}-${BN}"
    URL="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$VER/spigot-api-${FVER}.jar"
    curl -skL -A "Mozilla/5.0" -o "$API_JAR" "$URL"
    echo "[build] Downloaded $API_JAR"
fi

echo "[build] Compiling ..."
"$JAVAC" -cp "$API_JAR" -d target/classes $(find src/main/java -name '*.java')
cp src/main/resources/plugin.yml target/classes/

OUT="target/Btn-mods-1.0.0.jar"
echo "[build] Packaging $OUT ..."
"$JAR" cfe "$OUT" com.qvietdpzai.btnmods.BtnMods -C target/classes .
echo "[build] Done: $OUT"
