#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skillhub
TARGET="${1:-all}"

current_release() {
  readlink -f "$BASE/current" 2>/dev/null || echo '<missing>'
}

current_release_file() {
  local name="$1"
  local dir
  dir="$(current_release)"
  [ -d "$dir" ] && [ -f "$dir/$name" ] && echo "$dir/$name"
}

server_env_summary() {
  docker inspect skillhub-server-1 --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | grep -E '^(SKILLHUB_PUBLIC_BASE_URL|SKILLHUB_AUTH_DINGTALK_REDIRECT_URI|SKILLHUB_STORAGE_PROVIDER|SESSION_COOKIE_SECURE)=' | sort || true
}

web_env_summary() {
  docker inspect skillhub-web-1 --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | grep -E '^(SKILLHUB_PUBLIC_BASE_URL|SKILLHUB_API_UPSTREAM|SKILLHUB_WEB_AUTH_DINGTALK_)=' | sort || true
}

show_release_summary() {
  local manifest deploy_log verify_log rollback_log
  manifest="$(current_release_file manifest.json || true)"
  deploy_log="$(current_release_file deploy.log || true)"
  verify_log="$(current_release_file verify.log || true)"
  rollback_log="$(current_release_file rollback.log || true)"
  echo '--- release summary ---'
  if [ -n "$manifest" ]; then
    sed -n '1,40p' "$manifest"
  else
    echo '<no manifest>'
  fi
  echo '--- recent deploy log ---'
  if [ -n "$deploy_log" ]; then tail -n 5 "$deploy_log"; else echo '<no deploy log>'; fi
  echo '--- recent verify log ---'
  if [ -n "$verify_log" ]; then tail -n 5 "$verify_log"; else echo '<no verify log>'; fi
  echo '--- recent rollback log ---'
  if [ -n "$rollback_log" ]; then tail -n 5 "$rollback_log"; else echo '<no rollback log>'; fi
}

show_all() {
  printf 'current -> %s\n' "$(current_release)"
  echo '--- containers ---'
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' | grep -i skillhub || true
  echo '--- prod health ---'
  curl -fsS http://127.0.0.1:8080/actuator/health || true
  echo
  echo '--- prod web ---'
  curl -I -s http://127.0.0.1/ | sed -n '1,8p' || true
  show_release_summary
}

show_server() {
  printf 'current -> %s\n' "$(current_release)"
  echo '--- server container ---'
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' | grep '^skillhub-server-1' || true
  echo '--- server health ---'
  curl -fsS http://127.0.0.1:8080/actuator/health || true
  echo
  echo '--- server env ---'
  server_env_summary
  show_release_summary
}

show_web() {
  printf 'current -> %s\n' "$(current_release)"
  echo '--- web container ---'
  docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' | grep '^skillhub-web-1' || true
  echo '--- web head ---'
  curl -I -s http://127.0.0.1/ | sed -n '1,8p' || true
  echo '--- web env ---'
  web_env_summary
  show_release_summary
}

case "$TARGET" in
  all) show_all ;;
  server) show_server ;;
  web) show_web ;;
  *) echo 'usage: status.sh [all|server|web]' >&2; exit 2 ;;
esac
