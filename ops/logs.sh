#!/usr/bin/env bash
set -euo pipefail
TARGET="${1:-all}"
case "$TARGET" in
  web) docker logs --tail=200 -f skillhub-web-1 ;;
  server) docker logs --tail=200 -f skillhub-server-1 ;;
  postgres) docker logs --tail=200 -f skillhub-postgres-1 ;;
  redis) docker logs --tail=200 -f skillhub-redis-1 ;;
  all)
    echo 'Use one of: web | server | postgres | redis'
    ;;
  *)
    echo "Unknown target: $TARGET" >&2
    exit 2
    ;;
esac
