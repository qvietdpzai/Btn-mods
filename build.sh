#!/usr/bin/env bash
# Build mod(s) cho server Paper 26.2 / Minecraft 26.2 / Java 25.
#
# Dung: ./build.sh                -> build tat ca mod trong mods/
#       ./build.sh Btn-checkhealth -> build 1 mod cu the
#
# Moi mod: mods/<Ten-mod>/src/main/java + src/main/resources
set -euo pipefail

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

# YAMLCHECK: phat hien dau ":" chua quote trong plugin.yml/config.yml
# (neu thieu se loi InvalidDescriptionException khi Paper doc plugin.yml)
python3 - <<'PYEOF'
import glob, re, sys
bad = []
for f in glob.glob('mods/*/src/main/resources/plugin.yml') + glob.glob('mods/*/src/main/resources/config.yml'):
    for i, line in enumerate(open(f, encoding='utf-8'), 1):
        m = re.match(r'^(\s*[\w-]+):\s+(.*)$', line.rstrip())
        if not m:
            continue
        val = m.group(2)
        if not val or val[0] in "#'\"[{":
            continue
        if re.search(r'[A-Za-z0-9]+:\s', val) or val.endswith(':'):
            bad.append("%s:%d: %s" % (f, i, line.strip()))
if bad:
    print("[build] LOI: dau ':' chua quote (Paper se khong doc duoc plugin.yml):")
    for b in bad:
        print("   " + b)
    sys.exit(1)
PYEOF

# YAMLPARSE: kiem tra MOI file .yml trong resources PARSE DUOC.
# Ly do: plugin.yml/config.yml hop le KHONG co nghia la shops.yml/vi.yml hop le.
# Neu shops.yml lech lech thut 1 space -> Paper khong doc duoc -> shop trong (0 item).
# TODO can python3-yaml: sudo apt-get install python3-yaml
python3 - <<'PYEOF'
import glob, sys
try:
    import yaml
except ImportError:
    print("[build] CANH BAO: chua cai python3-yaml -> bo qua kiem tra parse YAML")
    print("           Cai bang: sudo apt-get install python3-yaml")
    sys.exit(0)

bad = 0
checked = 0
for f in sorted(glob.glob('mods/*/src/main/resources/**/*.yml', recursive=True)):
    checked += 1
    try:
        with open(f, encoding='utf-8') as fh:
            yaml.safe_load(fh)
    except Exception as e:
        bad += 1
        msg = str(e).replace('\n', '\n     ')
        print("[build] LOI PARSE YAML: %s" % f)
        print("     %s" % msg)

if bad:
    print("[build] ===> %d file YAML loi (da kiem tra %d file)." % (bad, checked))
    print("           Sua lech thut le (indentation) roi build lai.")
    sys.exit(1)
else:
    print("[build] YAML parse OK: %d file" % checked)
PYEOF

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
    "$JAVAC" -cp "$API_JAR:build-lib/VaultAPI.jar" -d "$classes" $(find "$dir/src/main/java" -name '*.java') || return 1
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
