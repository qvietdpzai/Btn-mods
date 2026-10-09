#!/usr/bin/env bash
# Deploy Btn-mods to nvnmc hosting via SFTP, then delete local copy.
# Usage: ./deploy.sh [jar-path]
set -euo pipefail

SFTP_HOST="sftp.nvnmc.asia"
SFTP_PORT="2023"
SFTP_USER="vietp8082yvt.b4e77c30"
SFTP_PASS="qviet1234@"
REMOTE_DIR="plugins"

JAR="${1:-}"
if [[ -z "$JAR" ]]; then
    # auto-detect latest built jar in target/
    JAR="$(ls -t target/*.jar 2>/dev/null | head -n1 || true)"
fi
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
    echo "[deploy] No jar found. Run 'mvn package' first or pass a jar path." >&2
    exit 1
fi

JAR_NAME="$(basename "$JAR")"
echo "[deploy] Uploading $JAR_NAME -> sftp://$SFTP_HOST:$SFTP_PORT/$REMOTE_DIR/"

# Upload via sftp batch
printf "cd %s\nput %s\nbye\n" "$REMOTE_DIR" "$JAR" | \
    sshpass -p "$SFTP_PASS" sftp -P "$SFTP_PORT" \
        -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
        "$SFTP_USER@$SFTP_HOST"

echo "[deploy] Upload complete. Removing local copy: $JAR"
rm -f "$JAR"
echo "[deploy] Done."
