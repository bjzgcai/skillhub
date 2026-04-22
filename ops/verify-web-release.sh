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

ok=0
for i in 1 2 3 4 5 6 7 8 9 10; do
  code="$(curl -o /tmp/skillhub.verify.web.html -s -w '%{http_code}' http://127.0.0.1/ || true)"
  if [ "$code" = "200" ] && grep -Eq '<title>SkillHub([^<]*)</title>' /tmp/skillhub.verify.web.html; then
    ok=1
    break
  fi
  sleep 2
done
[ "$ok" -eq 1 ] || { echo 'web verify failed' >&2; exit 1; }

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
