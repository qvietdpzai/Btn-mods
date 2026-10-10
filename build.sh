#!/usr/bin/env bash
# Build mod(s) cho server Paper 26.2 / Minecraft 26.2 / Java 25.
#
# Dung: ./build.sh                -> build tat ca mod trong mods/
#       ./build.sh Btn-checkhealth -> build 1 mod cu the
#
# Moi mod: mods/<Ten-mod>/src/main/java + src/main/resources
set -uo pipefail

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
JAVAC="$JAVA_HOME/bin/javac"
JARC="$JAVA_HOME/bin/jar"

API_DIR="build-lib"
API_JAR="$API_DIR/spigot-api.jar"
VER="26.2-R0.1-SNAPSHOT"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

mkdir -p "$API_DIR"
if [[ ! -s "$API_JAR" ]]; then
    echo "[build] Tai spigot-api $VER ..."
    META="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$VER/maven-metadata.xml"
    XML="$(curl -skL -A "$UA" "$META")"
    TS="$(printf '%s' "$XML" | grep -o '<timestamp>[^<]*</timestamp>' | head -1 | sed 's/[^0-9.]//g')"
    BN="$(printf '%s' "$XML" | grep -o '<buildNumber>[^<]*</buildNumber>' | head -1 | sed 's/[^0-9]//g')"
    FVER="${VER%-SNAPSHOT}-${TS}-${BN}"
    URL="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/$VER/spigot-api-${FVER}.jar"
    curl -skL -A "$UA" -o "$API_JAR" "$URL"
fi

build_one() {
    local mod="$1"
    local dir="mods/$mod"
    [[ -d "$dir" ]] || { echo "[build] Khong thay mod: $mod"; return 1; }

    local main
    main="$(grep -m1 '^main:' "$dir/src/main/resources/plugin.yml" | awk '{print $2}')"
    local name version
    name="$(grep -m1 '^name:' "$dir/src/main/resources/plugin.yml" | awk '{print $2}')"
    version="$(grep -m1 '^version:' "$dir/src/main/resources/plugin.yml" | awk '{print $2}')"
    [[ -n "$main" ]] || { echo "[build] $mod: thieu 'main:' trong plugin.yml"; return 1; }

    local out="target/${name}-${version}.jar"
    local classes="target/classes-$mod"
    rm -rf "$classes" && mkdir -p "$classes" "$(dirname "$out")"

    echo "[build] Compile $mod ..."
    # shellcheck disable=SC2046
    "$JAVAC" -cp "$API_JAR" -d "$classes" $(find "$dir/src/main/java" -name '*.java') || return 1
    cp -r "$dir/src/main/resources/." "$classes/"

    echo "[build] Dong goi $out (main=$main) ..."
    "$JARC" cfe "$out" "$main" -C "$classes" . || return 1
    echo "[build] Xong: $out"
}

FAIL=0
if [[ $# -ge 1 ]]; then
    build_one "$1" || FAIL=1
else
    for d in mods/*/; do
        build_one "$(basename "$d")" || FAIL=1
    done
fi
exit $FAIL
