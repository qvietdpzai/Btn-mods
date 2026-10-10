#!/usr/bin/env bash
# Build Btn-checkhealth plugin (Spigot API 26.2 / Minecraft 26.2 - Paper 26.2-129).
# Tu tai spigot-api snapshot neu thieu, compile voi javac JDK 25, dong goi jar.
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"

API_DIR="libs"
API_JAR="$API_DIR/spigot-api.jar"
VER="26.2-R0.1-SNAPSHOT"
MAIN="com.qvietdpzai.btnhealth.BtnCheckHealth"
OUT="target/Btn-checkhealth-1.0.0.jar"

mkdir -p "$API_DIR" target/classes target

if [[ ! -s "$API_JAR" ]]; then
    echo "[build] Tai spigot-api $VER ..."
    UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    META="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$VER/maven-metadata.xml"
    XML="$(curl -skL -A "$UA" "$META")"
    TS="$(printf '%s' "$XML" | grep -o '<timestamp>[^<]*</timestamp>' | head -1 | sed 's/[^0-9.]//g')"
    BN="$(printf '%s' "$XML" | grep -o '<buildNumber>[^<]*</buildNumber>' | head -1 | sed 's/[^0-9]//g')"
    FVER="${VER%-SNAPSHOT}-${TS}-${BN}"
    URL="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$VER/spigot-api-${FVER}.jar"
    curl -skL -A "$UA" -o "$API_JAR" "$URL"
    echo "[build] Da tai $API_JAR"
fi

echo "[build] Compile ..."
rm -rf target/classes && mkdir -p target/classes
"$JAVAC" -cp "$API_JAR" -d target/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/* target/classes/

echo "[build] Dong goi $OUT ..."
"$JAR" cfe "$OUT" "$MAIN" -C target/classes .
echo "[build] Xong: $OUT"
