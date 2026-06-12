#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skillhub
SHARED="$BASE/shared"
RELEASES="$BASE/releases"
TEMPLATES="$RELEASES/templates"

require_file() {
  local f="$1"
  [ -f "$f" ] || { echo "missing required file: $f" >&2; exit 1; }
}

load_env_files() {
  require_file "$SHARED/env.release"
  require_file "$SHARED/secrets.env"
  set -a
  # shellcheck disable=SC1091
  . "$SHARED/env.release"
  # shellcheck disable=SC1091
  . "$SHARED/secrets.env"
  set +a
}

new_release_id() {
  date -u +%Y%m%dT%H%M%SZ
}

render_release() {
  local release_id="$1"
  local out_dir="$RELEASES/$release_id"
  mkdir -p "$out_dir"
  require_file "$TEMPLATES/compose.release.yml.tpl"
  envsubst < "$TEMPLATES/compose.release.yml.tpl" > "$out_dir/compose.release.yml"
  cat > "$out_dir/release.json" <<JSON
{
  "releaseId": "$release_id",
  "generatedAtUtc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "serverImage": "${SKILLHUB_SERVER_IMAGE}:${SKILLHUB_SERVER_TAG}",
  "webImage": "${SKILLHUB_WEB_IMAGE}:${SKILLHUB_WEB_TAG}",
  "publicBaseUrl": "${SKILLHUB_PUBLIC_BASE_URL}",
  "apiUpstream": "${SKILLHUB_API_UPSTREAM}"
}
JSON
  cp "$SHARED/env.release" "$out_dir/release.env"
  chmod 640 "$out_dir/release.env" || true
  echo "$out_dir"
}

get_previous_release_dir() {
  if [ ! -L "$BASE/current" ] && [ ! -d "$BASE/current" ]; then
    return 1
  fi
  readlink -f "$BASE/current"
}

release_id_from_dir() {
  basename "$1"
}

is_successful_release_dir() {
  local dir="$1"
  [ -f "$dir/release.env" ] || return 1
  [ -f "$dir/release.json" ] || return 1
  [ -f "$dir/verify.log" ] || return 1
  grep -Eq '(^|[[:space:]])(verify ok|web verify ok|server verify ok)$' "$dir/verify.log"
}

find_latest_previous_release_dir() {
  local exclude_dir="$1"
  find "$RELEASES" -mindepth 1 -maxdepth 1 -type d ! -name templates | sort -r | while read -r dir; do
    [ "$dir" = "$exclude_dir" ] && continue
    is_successful_release_dir "$dir" || continue
    echo "$dir"
    return 0
  done
  return 1
}

server_image_ref() {
  echo "${SKILLHUB_SERVER_IMAGE}:${SKILLHUB_SERVER_TAG}"
}

web_image_ref() {
  echo "${SKILLHUB_WEB_IMAGE}:${SKILLHUB_WEB_TAG}"
}

gitleaks_scanner_image_ref() {
  echo "${SKILLHUB_GITLEAKS_SCANNER_IMAGE:-skillhub-gitleaks-scanner}:${SKILLHUB_GITLEAKS_SCANNER_TAG:-latest}"
}

unified_scanner_image_ref() {
  echo "${SKILLHUB_SECURITY_SCANNER_IMAGE:-skill-security-scanner}:${SKILLHUB_SECURITY_SCANNER_TAG:-latest}"
}

server_current_image() {
  docker inspect skillhub-server-1 --format '{{.Config.Image}}'
}

web_current_image() {
  docker inspect skillhub-web-1 --format '{{.Config.Image}}'
}

gitleaks_scanner_current_image() {
  docker inspect skillhub-gitleaks-scanner-1 --format '{{.Config.Image}}' 2>/dev/null || true
}

unified_scanner_current_image() {
  docker inspect skillhub-security-scanner-1 --format '{{.Config.Image}}' 2>/dev/null || true
}

container_env_value() {
  local container="$1"
  local key="$2"
  docker inspect "$container" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | awk -F= -v key="$key" '$1 == key {print substr($0, length(key) + 2); exit}'
}

server_storage_volume() {
  echo "${SKILLHUB_STORAGE_VOLUME:-skillhub_skillhub_storage}"
}

remove_container_if_exists() {
  local name="$1"
  local cid
  cid="$(docker ps -aqf name="^${name}$" || true)"
  if [ -n "$cid" ]; then
    docker rm -f "$name" >/dev/null
  fi
}

run_gitleaks_scanner_container() {
  local image_ref="${1:-}"
  [ -n "$image_ref" ] || image_ref="$(gitleaks_scanner_image_ref)"
  docker image inspect "$image_ref" >/dev/null 2>&1 || { echo "gitleaks scanner image not found locally: $image_ref" >&2; exit 4; }
  docker run -d \
    --name skillhub-gitleaks-scanner-1 \
    --network skillhub_default \
    --restart unless-stopped \
    -e GITLEAKS_TIMEOUT_SECONDS="${GITLEAKS_TIMEOUT_SECONDS:-30}" \
    -e GITLEAKS_MAX_FINDINGS="${GITLEAKS_MAX_FINDINGS:-50}" \
    "$image_ref"
}

ensure_gitleaks_scanner_container() {
  if [ "${SKILLHUB_SECRET_SCAN_ENABLED:-false}" != "true" ]; then
    return 0
  fi
  local target_image current_image
  target_image="$(gitleaks_scanner_image_ref)"
  current_image="$(gitleaks_scanner_current_image)"
  if [ "$current_image" = "$target_image" ] && [ -n "$(docker ps -qf name='^skillhub-gitleaks-scanner-1$' || true)" ]; then
    return 0
  fi
  remove_container_if_exists skillhub-gitleaks-scanner-1
  run_gitleaks_scanner_container "$target_image" >/tmp/skillhub.deploy.gitleaks-scanner.cid
}

restore_gitleaks_scanner_container() {
  local previous_enabled="$1"
  local previous_image="$2"
  remove_container_if_exists skillhub-gitleaks-scanner-1
  if [ "$previous_enabled" = "true" ] && [ -n "$previous_image" ]; then
    run_gitleaks_scanner_container "$previous_image" >/tmp/skillhub.rollback.gitleaks-scanner.cid
  fi
}

run_unified_scanner_container() {
  local image_ref="${1:-}"
  [ -n "$image_ref" ] || image_ref="$(unified_scanner_image_ref)"
  docker image inspect "$image_ref" >/dev/null 2>&1 || { echo "unified scanner image not found locally: $image_ref" >&2; exit 4; }
  docker run -d \
    --name skillhub-security-scanner-1 \
    --network skillhub_default \
    --restart unless-stopped \
    "$image_ref"
}

ensure_unified_scanner_container() {
  if [ "${SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED:-false}" != "true" ]; then
    return 0
  fi
  local target_image current_image
  target_image="$(unified_scanner_image_ref)"
  current_image="$(unified_scanner_current_image)"
  if [ "$current_image" = "$target_image" ] && [ -n "$(docker ps -qf name='^skillhub-security-scanner-1$' || true)" ]; then
    return 0
  fi
  remove_container_if_exists skillhub-security-scanner-1
  run_unified_scanner_container "$target_image" >/tmp/skillhub.deploy.unified-scanner.cid
}

restore_unified_scanner_container() {
  local previous_enabled="$1"
  local previous_image="$2"
  remove_container_if_exists skillhub-security-scanner-1
  if [ "$previous_enabled" = "true" ] && [ -n "$previous_image" ]; then
    run_unified_scanner_container "$previous_image" >/tmp/skillhub.rollback.unified-scanner.cid
  fi
}

restore_missing_web_assets() {
  local old_assets_dir="$1"
  [ -d "$old_assets_dir" ] || return 0
  [ -n "$(find "$old_assets_dir" -type f -print -quit)" ] || return 0
  find "$old_assets_dir" -type f | while IFS= read -r asset; do
    local rel_path="${asset#"$old_assets_dir"/}"
    if ! docker exec skillhub-web-1 test -f "/usr/share/nginx/html/assets/$rel_path"; then
      local parent_dir
      parent_dir="$(dirname "$rel_path")"
      docker exec skillhub-web-1 mkdir -p "/usr/share/nginx/html/assets/$parent_dir"
      docker cp "$asset" "skillhub-web-1:/usr/share/nginx/html/assets/$rel_path" >/dev/null
    fi
  done
}

run_server_container() {
  local image_ref="$1"
  local env_file="$2"
  docker run -d \
    --name skillhub-server-1 \
    --network skillhub_default \
    --restart unless-stopped \
    -p 8080:8080 \
    --env-file "$env_file" \
    --env-file "$SHARED/secrets.env" \
    -e SPRING_PROFILES_ACTIVE=docker \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/${POSTGRES_DB:-skillhub}" \
    -e SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-skillhub}" \
    -e SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD:-skillhub_demo}" \
    -e REDIS_HOST=redis \
    -e REDIS_PORT=6379 \
    -e SKILLHUB_SECRET_SCAN_ENABLED="${SKILLHUB_SECRET_SCAN_ENABLED:-false}" \
    -e SKILLHUB_SECRET_SCAN_BASE_URL="${SKILLHUB_SECRET_SCAN_BASE_URL:-http://skillhub-gitleaks-scanner-1:8015}" \
    -e SKILLHUB_SECRET_SCAN_READ_TIMEOUT="${SKILLHUB_SECRET_SCAN_READ_TIMEOUT:-30000}" \
    -e SKILLHUB_SECRET_SCAN_FAIL_CLOSED="${SKILLHUB_SECRET_SCAN_FAIL_CLOSED:-true}" \
    -e SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED="${SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED:-false}" \
    -e SKILLHUB_SECURITY_UNIFIED_SCAN_BASE_URL="${SKILLHUB_SECURITY_UNIFIED_SCAN_BASE_URL:-http://skillhub-security-scanner-1:8020}" \
    -e SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_WARN="${SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_WARN:-false}" \
    -e SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_MANUAL_REVIEW="${SKILLHUB_SECURITY_UNIFIED_SCAN_BLOCK_MANUAL_REVIEW:-false}" \
    -e SKILLHUB_SECURITY_UNIFIED_SCAN_FAIL_CLOSED="${SKILLHUB_SECURITY_UNIFIED_SCAN_FAIL_CLOSED:-true}" \
    -e STORAGE_BASE_PATH=/var/lib/skillhub/storage \
    -v "$(server_storage_volume)":/var/lib/skillhub/storage \
    "$image_ref"
}

run_web_container() {
  local image_ref="$1"
  local web_bind_address="${SKILLHUB_WEB_BIND_ADDRESS:-${WEB_BIND_ADDRESS:-0.0.0.0}}"
  local web_port="${SKILLHUB_WEB_PORT:-${WEB_PORT:-80}}"
  docker run -d \
    --name skillhub-web-1 \
    --network skillhub_default \
    --restart unless-stopped \
    -p "${web_bind_address}:${web_port}:80" \
    -e SKILLHUB_API_UPSTREAM="${SKILLHUB_API_UPSTREAM}" \
    -e SKILLHUB_PUBLIC_BASE_URL="${SKILLHUB_PUBLIC_BASE_URL}" \
    -e SKILLHUB_WEB_API_BASE_URL="${SKILLHUB_WEB_API_BASE_URL:-}" \
    -e SKILLHUB_WEB_AUTH_DINGTALK_ENABLED="${SKILLHUB_WEB_AUTH_DINGTALK_ENABLED:-false}" \
    -e SKILLHUB_WEB_AUTH_DINGTALK_PROVIDER="${SKILLHUB_WEB_AUTH_DINGTALK_PROVIDER:-dingtalk}" \
    -e SKILLHUB_WEB_AUTH_DINGTALK_AUTO="${SKILLHUB_WEB_AUTH_DINGTALK_AUTO:-false}" \
    -e SKILLHUB_WEB_AUTH_DINGTALK_CORP_ID="${SKILLHUB_WEB_AUTH_DINGTALK_CORP_ID:-}" \
    "$image_ref"
}

write_manifest() {
  local out_dir="$1"
  local component="$2"
  local previous_release_dir="${3:-}"
  local previous_server_image="${4:-}"
  local previous_web_image="${5:-}"
  local target_server_image="${6:-}"
  local target_web_image="${7:-}"
  cat > "$out_dir/manifest.json" <<JSON
{
  "releaseId": "$(basename "$out_dir")",
  "component": "$component",
  "generatedAtUtc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "previousReleaseDir": "$previous_release_dir",
  "previousServerImage": "$previous_server_image",
  "previousWebImage": "$previous_web_image",
  "targetServerImage": "$target_server_image",
  "targetWebImage": "$target_web_image",
  "publicBaseUrl": "${SKILLHUB_PUBLIC_BASE_URL}",
  "dingtalkRedirectUri": "${SKILLHUB_AUTH_DINGTALK_REDIRECT_URI:-}",
  "apiUpstream": "${SKILLHUB_API_UPSTREAM}",
  "storageVolume": "$(server_storage_volume)"
}
JSON
}

append_release_log() {
  local out_dir="$1"
  local file="$2"
  shift 2
  printf '[%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >> "$out_dir/$file"
}
