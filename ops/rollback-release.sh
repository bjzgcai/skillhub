#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skillhub
# shellcheck disable=SC1091
. "$BASE/ops/release-lib.sh"

COMPONENT=""
TARGET_RELEASE=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --component) COMPONENT="$2"; shift 2 ;;
    --to) TARGET_RELEASE="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

[ -n "$COMPONENT" ] || { echo '--component is required' >&2; exit 2; }
case "$COMPONENT" in
  server|web|all) ;;
  *) echo 'rollback currently supports only: --component server|web|all' >&2; exit 2 ;;
esac

load_env_files
current_dir="$(get_previous_release_dir)"
if [ -n "$TARGET_RELEASE" ]; then
  target_dir="$BASE/releases/$TARGET_RELEASE"
else
  target_dir="$(find_latest_previous_release_dir "$current_dir")"
fi

[ -n "$target_dir" ] && [ -d "$target_dir" ] || { echo 'target release not found' >&2; exit 1; }
[ -f "$target_dir/release.env" ] || { echo "missing target release env: $target_dir/release.env" >&2; exit 1; }
[ -f "$target_dir/release.json" ] || { echo "missing target release json: $target_dir/release.json" >&2; exit 1; }
if [ "${SKILLHUB_ALLOW_UNVERIFIED_ROLLBACK:-false}" != "true" ] && ! is_successful_release_dir "$target_dir"; then
  echo "target release is not verified: $target_dir" >&2
  echo 'set SKILLHUB_ALLOW_UNVERIFIED_ROLLBACK=true to override intentionally' >&2
  exit 1
fi

read_json() {
  python3 - <<'PY' "$1" "$2"
import json, sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    data = json.load(f)
print(data.get(sys.argv[2], ''))
PY
}

read_env_value() {
  local file="$1" key="$2"
  (grep -E "^${key}=" "$file" | head -n1 | sed "s/^${key}=//") || true
}

target_server_image="$(read_json "$target_dir/release.json" targetServerImage)"
[ -n "$target_server_image" ] || target_server_image="$(read_json "$target_dir/release.json" serverImage)"
if [ -z "$target_server_image" ]; then
  target_server_image="$(read_env_value "$target_dir/release.env" SKILLHUB_SERVER_IMAGE):$(read_env_value "$target_dir/release.env" SKILLHUB_SERVER_TAG)"
fi

target_web_image="$(read_json "$target_dir/release.json" targetWebImage)"
[ -n "$target_web_image" ] || target_web_image="$(read_json "$target_dir/release.json" webImage)"
if [ -z "$target_web_image" ]; then
  web_image_name="$(read_env_value "$target_dir/release.env" SKILLHUB_WEB_IMAGE)"
  web_tag="$(read_env_value "$target_dir/release.env" SKILLHUB_WEB_TAG)"
  if [ -z "$web_tag" ]; then
    web_tag="$(read_env_value "$target_dir/release.env" SKILLHUB_VERSION)"
  fi
  if [ -n "$web_image_name" ] && [ -n "$web_tag" ]; then
    target_web_image="${web_image_name}:${web_tag}"
  fi
fi

expected_public_base_url="$(read_env_value "$target_dir/release.env" SKILLHUB_PUBLIC_BASE_URL)"
expected_dingtalk_redirect_uri="$(read_env_value "$target_dir/release.env" SKILLHUB_AUTH_DINGTALK_REDIRECT_URI)"
expected_api_upstream="$(read_env_value "$target_dir/release.env" SKILLHUB_API_UPSTREAM)"

if [ "$COMPONENT" = "server" ] || [ "$COMPONENT" = "all" ]; then
  [ -n "$target_server_image" ] || { echo 'target release missing server image' >&2; exit 1; }
  docker image inspect "$target_server_image" >/dev/null 2>&1 || { echo "target image not found locally: $target_server_image" >&2; exit 1; }
  remove_container_if_exists skillhub-server-1
  run_server_container "$target_server_image" "$target_dir/release.env" >/tmp/skillhub.rollback.server.cid
  "$BASE/ops/verify-server-release.sh" \
    --expect-public-base-url "$expected_public_base_url" \
    --expect-dingtalk-redirect-uri "$expected_dingtalk_redirect_uri" \
    --expect-env-file "$target_dir/release.env"
fi

if [ "$COMPONENT" = "web" ] || [ "$COMPONENT" = "all" ]; then
  [ -n "$target_web_image" ] || { echo 'target release missing web image' >&2; exit 1; }
  docker image inspect "$target_web_image" >/dev/null 2>&1 || { echo "target image not found locally: $target_web_image" >&2; exit 1; }
  set -a
  . "$target_dir/release.env"
  set +a
  remove_container_if_exists skillhub-web-1
  run_web_container "$target_web_image" >/tmp/skillhub.rollback.web.cid
  "$BASE/ops/verify-web-release.sh" \
    --expect-public-base-url "$expected_public_base_url" \
    --expect-api-upstream "$expected_api_upstream"
fi

ln -sfn "$target_dir" "$BASE/current"
printf '[%s] rollback component=%s target=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$COMPONENT" "$(release_id_from_dir "$target_dir")" >> "$target_dir/rollback.log"
echo "$COMPONENT rollback applied successfully: $(release_id_from_dir "$target_dir")"
