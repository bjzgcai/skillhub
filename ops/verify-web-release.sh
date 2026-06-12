#!/usr/bin/env bash
set -euo pipefail
EXPECT_PUBLIC_BASE_URL=""
EXPECT_API_UPSTREAM=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --expect-public-base-url) EXPECT_PUBLIC_BASE_URL="$2"; shift 2 ;;
    --expect-api-upstream) EXPECT_API_UPSTREAM="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

web_cid="$(docker ps -qf name='^skillhub-web-1$' || true)"
[ -n "$web_cid" ] || { echo 'web container not running' >&2; exit 1; }

published_port="$(docker port skillhub-web-1 80/tcp 2>/dev/null | head -n1 || true)"
if [ -n "$published_port" ]; then
  verify_host="${published_port%:*}"
  web_port="${published_port##*:}"
else
  verify_host="${SKILLHUB_WEB_BIND_ADDRESS:-${WEB_BIND_ADDRESS:-127.0.0.1}}"
  web_port="${SKILLHUB_WEB_PORT:-${WEB_PORT:-80}}"
fi
if [ "$verify_host" = "0.0.0.0" ] || [ "$verify_host" = "::" ]; then
  verify_host="127.0.0.1"
fi
verify_url="http://${verify_host}:${web_port}/"

ok=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  code="$(curl -o /tmp/skillhub.verify.web.html -s -w '%{http_code}' "$verify_url" || true)"
  if [ "$code" = "200" ] && grep -Eq '<title>SkillHub([^<]*)</title>' /tmp/skillhub.verify.web.html; then
    ok=1
    break
  fi
  sleep 2
done
[ "$ok" -eq 1 ] || { echo "web verify failed: $verify_url" >&2; exit 1; }

env_dump="$(docker inspect skillhub-web-1 --format '{{range .Config.Env}}{{println .}}{{end}}')"
for key in SKILLHUB_PUBLIC_BASE_URL SKILLHUB_API_UPSTREAM; do
  echo "$env_dump" | grep -q "^${key}=" || { echo "missing web env: ${key}" >&2; exit 1; }
done

if [ -n "$EXPECT_PUBLIC_BASE_URL" ]; then
  echo "$env_dump" | grep -Fx "SKILLHUB_PUBLIC_BASE_URL=${EXPECT_PUBLIC_BASE_URL}" >/dev/null || {
    echo 'web public base url mismatch' >&2
    exit 1
  }
fi

if [ -n "$EXPECT_API_UPSTREAM" ]; then
  echo "$env_dump" | grep -Fx "SKILLHUB_API_UPSTREAM=${EXPECT_API_UPSTREAM}" >/dev/null || {
    echo 'web api upstream mismatch' >&2
    exit 1
  }
fi

echo 'web verify ok'
