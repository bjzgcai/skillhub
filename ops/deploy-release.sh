#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skillhub
# shellcheck disable=SC1091
. "$BASE/ops/release-lib.sh"

COMPONENT="plan"
SERVER_TAG_OVERRIDE=""
WEB_TAG_OVERRIDE=""
APPLY=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --component) COMPONENT="$2"; shift 2 ;;
    --server-tag) SERVER_TAG_OVERRIDE="$2"; shift 2 ;;
    --web-tag) WEB_TAG_OVERRIDE="$2"; shift 2 ;;
    --apply) APPLY=1; shift ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

load_env_files

: "${SKILLHUB_SERVER_IMAGE:=skillhub-server}"
: "${SKILLHUB_WEB_IMAGE:=skillhub-web}"
: "${SKILLHUB_SERVER_TAG:=prod-latest-abdb516}"
: "${SKILLHUB_WEB_TAG:=prod-latest-abdb516}"
: "${POSTGRES_DB:=skillhub}"
: "${POSTGRES_USER:=skillhub}"
: "${SKILLHUB_API_UPSTREAM:=http://skillhub-server-1:8080}"
: "${SKILLHUB_PUBLIC_BASE_URL:=http://skills.zgci.org}"

[ -n "$SERVER_TAG_OVERRIDE" ] && SKILLHUB_SERVER_TAG="$SERVER_TAG_OVERRIDE"
[ -n "$WEB_TAG_OVERRIDE" ] && SKILLHUB_WEB_TAG="$WEB_TAG_OVERRIDE"

case "$COMPONENT" in
  plan|web|server|all) ;;
  *) echo "invalid --component: $COMPONENT" >&2; exit 2 ;;
esac

release_id="$(new_release_id)"
out_dir="$(render_release "$release_id")"
prev_release_dir="$(get_previous_release_dir || true)"

printf 'release planned: %s\n' "$release_id"
printf 'component: %s\n' "$COMPONENT"
printf 'dir: %s\n' "$out_dir"
printf 'server image: %s\n' "$(server_image_ref)"
printf 'web image: %s\n' "$(web_image_ref)"
append_release_log "$out_dir" deploy.log "release planned component=$COMPONENT server=$(server_image_ref) web=$(web_image_ref)"

if [ "$APPLY" -eq 0 ]; then
  write_manifest "$out_dir" "$COMPONENT" "$prev_release_dir" "" "" "$(server_image_ref)" "$(web_image_ref)"
  echo '--- rendered compose head ---'
  sed -n '1,120p' "$out_dir/compose.release.yml"
  exit 0
fi

apply_web() {
  local image_ref prev_image current_server_image old_assets_dir
  [ -n "$WEB_TAG_OVERRIDE" ] || { echo '--apply for web requires explicit --web-tag <tag>' >&2; exit 3; }
  image_ref="$(web_image_ref)"
  docker image inspect "$image_ref" >/dev/null 2>&1 || { echo "image not found locally: $image_ref" >&2; exit 4; }
  prev_image="$(web_current_image)"
  current_server_image="$(server_current_image)"
  old_assets_dir="$(mktemp -d /tmp/skillhub-web-assets.XXXXXX)"
  if docker ps -qf name='^skillhub-web-1$' >/dev/null; then
    docker cp skillhub-web-1:/usr/share/nginx/html/assets/. "$old_assets_dir/" >/dev/null 2>&1 || true
  fi
  write_manifest "$out_dir" "web" "$prev_release_dir" "$current_server_image" "$prev_image" "$current_server_image" "$image_ref"
  append_release_log "$out_dir" deploy.log "starting web deploy prev=$prev_image target=$image_ref"
  remove_container_if_exists skillhub-web-1
  run_web_container "$image_ref" >/tmp/skillhub.deploy.web.cid
  restore_missing_web_assets "$old_assets_dir"
  rm -rf "$old_assets_dir"
  if ! "$BASE/ops/verify-web-release.sh" >> "$out_dir/verify.log" 2>&1; then
    append_release_log "$out_dir" deploy.log "web verify failed, attempting rollback to $prev_image"
    remove_container_if_exists skillhub-web-1
    run_web_container "$prev_image" >/tmp/skillhub.rollback.web.cid
    "$BASE/ops/verify-web-release.sh" >> "$out_dir/verify.log" 2>&1
    exit 5
  fi
  append_release_log "$out_dir" deploy.log "web deploy applied successfully target=$image_ref"
}

apply_server() {
  local image_ref prev_image current_web_image prev_secret_scan_enabled prev_secret_scan_base_url prev_scanner_image prev_unified_scan_enabled prev_unified_scanner_image
  [ -n "$SERVER_TAG_OVERRIDE" ] || { echo '--apply for server requires explicit --server-tag <tag>' >&2; exit 3; }
  image_ref="$(server_image_ref)"
  docker image inspect "$image_ref" >/dev/null 2>&1 || { echo "image not found locally: $image_ref" >&2; exit 4; }
  prev_image="$(server_current_image)"
  current_web_image="$(web_current_image)"
  prev_secret_scan_enabled="$(container_env_value skillhub-server-1 SKILLHUB_SECRET_SCAN_ENABLED)"
  prev_secret_scan_base_url="$(container_env_value skillhub-server-1 SKILLHUB_SECRET_SCAN_BASE_URL)"
  prev_scanner_image="$(gitleaks_scanner_current_image)"
  prev_unified_scan_enabled="$(container_env_value skillhub-server-1 SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED)"
  prev_unified_scanner_image="$(unified_scanner_current_image)"
  write_manifest "$out_dir" "server" "$prev_release_dir" "$prev_image" "$current_web_image" "$image_ref" "$current_web_image"
  append_release_log "$out_dir" deploy.log "starting server deploy prev=$prev_image target=$image_ref"
  ensure_gitleaks_scanner_container
  ensure_unified_scanner_container
  remove_container_if_exists skillhub-server-1
  # Pre-deploy storage check
  if [ -x "$BASE/ops/verify-storage.sh" ]; then
    "$BASE/ops/verify-storage.sh" --pre --env-file "$SHARED/env.release" >> "$out_dir/verify.log" 2>&1 || {
      append_release_log "$out_dir" deploy.log "pre-deploy storage verify failed"
      exit 6
    }
  fi
  run_server_container "$image_ref" "$SHARED/env.release" >/tmp/skillhub.deploy.server.cid
  if ! "$BASE/ops/verify-server-release.sh" \
    --expect-public-base-url "$SKILLHUB_PUBLIC_BASE_URL" \
    --expect-dingtalk-redirect-uri "${SKILLHUB_AUTH_DINGTALK_REDIRECT_URI:-}" >> "$out_dir/verify.log" 2>&1; then
    append_release_log "$out_dir" deploy.log "server verify failed, attempting rollback to $prev_image"
    SKILLHUB_SECRET_SCAN_ENABLED="${prev_secret_scan_enabled:-false}"
    SKILLHUB_SECRET_SCAN_BASE_URL="${prev_secret_scan_base_url:-http://skillhub-gitleaks-scanner-1:8015}"
    SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED="${prev_unified_scan_enabled:-false}"
    restore_gitleaks_scanner_container "$SKILLHUB_SECRET_SCAN_ENABLED" "$prev_scanner_image"
    restore_unified_scanner_container "$SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED" "$prev_unified_scanner_image"
    remove_container_if_exists skillhub-server-1
    if [ -n "$prev_release_dir" ] && [ -f "$prev_release_dir/release.env" ]; then
      run_server_container "$prev_image" "$prev_release_dir/release.env" >/tmp/skillhub.rollback.server.cid
    else
      run_server_container "$prev_image" "$SHARED/env.release" >/tmp/skillhub.rollback.server.cid
    fi
    "$BASE/ops/verify-server-release.sh" >> "$out_dir/verify.log" 2>&1
    exit 5
  fi
  # Post-deploy storage verification
  if [ -x "$BASE/ops/verify-storage.sh" ]; then
    "$BASE/ops/verify-storage.sh" --post >> "$out_dir/verify.log" 2>&1 || {
      append_release_log "$out_dir" deploy.log "post-deploy storage verify failed, attempting rollback to $prev_image"
      remove_container_if_exists skillhub-server-1
      if [ -n "$prev_release_dir" ] && [ -f "$prev_release_dir/release.env" ]; then
        run_server_container "$prev_image" "$prev_release_dir/release.env" >/tmp/skillhub.rollback.server.cid
      else
        run_server_container "$prev_image" "$SHARED/env.release" >/tmp/skillhub.rollback.server.cid
      fi
      "$BASE/ops/verify-server-release.sh" >> "$out_dir/verify.log" 2>&1
      exit 5
    }
  fi
  append_release_log "$out_dir" deploy.log "server deploy applied successfully target=$image_ref"
}

case "$COMPONENT" in
  web)
    apply_web
    ln -sfn "$out_dir" "$BASE/current"
    echo "web deploy applied successfully: $(web_image_ref)"
    ;;
  server)
    apply_server
    ln -sfn "$out_dir" "$BASE/current"
    echo "server deploy applied successfully: $(server_image_ref)"
    ;;
  all)
    [ -n "$SERVER_TAG_OVERRIDE" ] || { echo '--apply for all requires explicit --server-tag <tag>' >&2; exit 3; }
    [ -n "$WEB_TAG_OVERRIDE" ] || { echo '--apply for all requires explicit --web-tag <tag>' >&2; exit 3; }
    write_manifest "$out_dir" "all" "$prev_release_dir" "$(server_current_image)" "$(web_current_image)" "$(server_image_ref)" "$(web_image_ref)"
    append_release_log "$out_dir" deploy.log "starting all deploy"
    apply_server
    apply_web
    "$BASE/ops/verify-release.sh" >> "$out_dir/verify.log" 2>&1
    append_release_log "$out_dir" deploy.log "all deploy applied successfully"
    ln -sfn "$out_dir" "$BASE/current"
    echo "all deploy applied successfully: server=$(server_image_ref) web=$(web_image_ref)"
    ;;
  *)
    echo 'stage-2 apply currently supports only: --component web|server|all' >&2
    exit 3
    ;;
esac
