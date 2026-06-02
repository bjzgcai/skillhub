#!/usr/bin/env bash
set -euo pipefail
BASE=/opt/skillhub
CHECK_DINGTALK=0
EXPECT_PUBLIC_BASE_URL=""
EXPECT_DINGTALK_REDIRECT_URI=""
EXPECT_ENV_FILE=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --check)
      case "$2" in
        dingtalk) CHECK_DINGTALK=1 ;;
        *) echo "unknown check: $2" >&2; exit 2 ;;
      esac
      shift 2
      ;;
    --expect-public-base-url) EXPECT_PUBLIC_BASE_URL="$2"; shift 2 ;;
    --expect-dingtalk-redirect-uri) EXPECT_DINGTALK_REDIRECT_URI="$2"; shift 2 ;;
    --expect-env-file) EXPECT_ENV_FILE="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

server_cid="$(docker ps -qf name='^skillhub-server-1$' || true)"
[ -n "$server_cid" ] || { echo 'server container not running' >&2; exit 1; }

if docker inspect skillhub-server-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -Fx 'SKILLHUB_SECRET_SCAN_ENABLED=true' >/dev/null; then
  scanner_ok=0
  for i in 1 2 3 4 5 6 7 8 9 10; do
    scanner_cid="$(docker ps -qf name='^skillhub-gitleaks-scanner-1$' || true)"
    if [ -n "$scanner_cid" ] && docker exec skillhub-gitleaks-scanner-1 wget -qO- http://127.0.0.1:8015/health | grep -q '"ok":true'; then
      scanner_ok=1
      break
    fi
    sleep 1
  done
  [ "$scanner_ok" -eq 1 ] || { echo 'gitleaks scanner health check failed' >&2; exit 1; }
fi

ok=0
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    ok=1
    break
  fi
  sleep 2
done
[ "$ok" -eq 1 ] || { echo 'server health check failed' >&2; exit 1; }

env_dump="$(docker inspect skillhub-server-1 --format '{{range .Config.Env}}{{println .}}{{end}}')"
for key in SKILLHUB_PUBLIC_BASE_URL SKILLHUB_AUTH_DINGTALK_REDIRECT_URI SKILLHUB_STORAGE_PROVIDER SESSION_COOKIE_SECURE; do
  echo "$env_dump" | grep -q "^${key}=" || { echo "missing server env: ${key}" >&2; exit 1; }
done

if [ -n "$EXPECT_PUBLIC_BASE_URL" ]; then
  echo "$env_dump" | grep -Fx "SKILLHUB_PUBLIC_BASE_URL=${EXPECT_PUBLIC_BASE_URL}" >/dev/null || {
    echo "server public base url mismatch" >&2
    exit 1
  }
fi

if [ -n "$EXPECT_DINGTALK_REDIRECT_URI" ]; then
  echo "$env_dump" | grep -Fx "SKILLHUB_AUTH_DINGTALK_REDIRECT_URI=${EXPECT_DINGTALK_REDIRECT_URI}" >/dev/null || {
    echo "server dingtalk redirect uri mismatch" >&2
    exit 1
  }
  CHECK_DINGTALK=1
fi

if [ -n "$EXPECT_ENV_FILE" ]; then
  [ -f "$EXPECT_ENV_FILE" ] || { echo "expected env file not found: $EXPECT_ENV_FILE" >&2; exit 1; }
  while IFS= read -r key; do
    [ -n "$key" ] || continue
    expected_line="$(grep -E "^${key}=" "$EXPECT_ENV_FILE" | head -n1 || true)"
    [ -n "$expected_line" ] || continue
    echo "$env_dump" | grep -Fx "$expected_line" >/dev/null || {
      echo "server env drift detected: ${key}" >&2
      exit 1
    }
  done <<'EOF'
SKILLHUB_PUBLIC_BASE_URL
SKILLHUB_AUTH_DINGTALK_REDIRECT_URI
SKILLHUB_STORAGE_PROVIDER
SESSION_COOKIE_SECURE
EOF
fi

if [ "$CHECK_DINGTALK" -eq 1 ]; then
  location="$(curl -s -D - -o /dev/null 'http://127.0.0.1:8080/api/v1/auth/dingtalk/authorize' | awk 'BEGIN{IGNORECASE=1} /^Location:/ {sub(/^Location:[[:space:]]*/, ""); print; exit}')"
  [ -n "$location" ] || { echo 'missing dingtalk authorize redirect location' >&2; exit 1; }
  if [ -n "$EXPECT_DINGTALK_REDIRECT_URI" ]; then
    encoded_expected="$(python3 - <<'PY' "$EXPECT_DINGTALK_REDIRECT_URI"
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
)"
    printf '%s\n' "$location" | grep -F "redirect_uri=${encoded_expected}" >/dev/null || {
      echo 'dingtalk authorize redirect_uri mismatch' >&2
      exit 1
    }
  fi
fi

echo 'server verify ok'
