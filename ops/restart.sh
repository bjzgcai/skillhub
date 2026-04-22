#!/usr/bin/env bash
set -euo pipefail
docker restart skillhub-postgres-1 skillhub-redis-1 skillhub-server-1 skillhub-web-1
/opt/skillhub/ops/verify-release.sh
