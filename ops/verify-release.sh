#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skillhub
TARGET="${1:-all}"
CURRENT_ENV_FILE=""
if [ -L "$BASE/current" ] || [ -d "$BASE/current" ]; then
  CURRENT_ENV_FILE="$(readlink -f "$BASE/current")/release.env"
fi

case "$TARGET" in
  all)
    "$BASE/ops/verify-server-release.sh" --expect-env-file "$CURRENT_ENV_FILE"
    "$BASE/ops/verify-web-release.sh"
    ;;
  server)
    "$BASE/ops/verify-server-release.sh" --expect-env-file "$CURRENT_ENV_FILE"
    ;;
  web)
    "$BASE/ops/verify-web-release.sh"
    ;;
  *) echo 'usage: verify-release.sh [all|server|web]' >&2; exit 2 ;;
esac

echo 'verify ok'
