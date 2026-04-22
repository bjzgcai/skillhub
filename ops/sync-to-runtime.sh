#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OPS_SRC="$REPO_ROOT/ops"
TEMPLATES_SRC="$OPS_SRC/templates"
RUNTIME_BASE="/opt/skillhub"
RUNTIME_OPS="$RUNTIME_BASE/ops"
RUNTIME_TEMPLATES="$RUNTIME_BASE/releases/templates"

mkdir -p "$RUNTIME_OPS" "$RUNTIME_TEMPLATES"

cp "$OPS_SRC"/*.sh "$RUNTIME_OPS/"
cp "$TEMPLATES_SRC"/* "$RUNTIME_TEMPLATES/"
chmod +x "$RUNTIME_OPS"/*.sh

bash -n "$RUNTIME_OPS/deploy-release.sh"
bash -n "$RUNTIME_OPS/release-lib.sh"
bash -n "$RUNTIME_OPS/verify-server-release.sh"
bash -n "$RUNTIME_OPS/verify-web-release.sh"
bash -n "$RUNTIME_OPS/verify-release.sh"
bash -n "$RUNTIME_OPS/rollback-release.sh"
bash -n "$RUNTIME_OPS/status.sh"

printf 'synced ops scripts to %s\n' "$RUNTIME_OPS"
printf 'synced templates to %s\n' "$RUNTIME_TEMPLATES"
find "$RUNTIME_OPS" -maxdepth 1 -type f | sort | sed 's#^#- #' 
find "$RUNTIME_TEMPLATES" -maxdepth 1 -type f | sort | sed 's#^#- #' 
