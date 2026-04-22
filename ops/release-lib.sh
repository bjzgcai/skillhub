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

find_latest_previous_release_dir() {
  local exclude_dir="$1"
  find "$RELEASES" -mindepth 1 -maxdepth 1 -type d ! -name templates | sort -r | while read -r dir; do
    [ "$dir" = "$exclude_dir" ] && continue
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

server_current_image() {
  docker inspect skillhub-server-1 --format '{{.Config.Image}}'
}

web_current_image() {
  docker inspect skillhub-web-1 --format '{{.Config.Image}}'
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
    -e STORAGE_BASE_PATH=/var/lib/skillhub/storage \
    -v "$(server_storage_volume)":/var/lib/skillhub/storage \
    "$image_ref"
}

run_web_container() {
  local image_ref="$1"
  docker run -d \
    --name skillhub-web-1 \
    --network skillhub_default \
    --restart unless-stopped \
    -p 80:80 \
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
