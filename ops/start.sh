#!/usr/bin/env bash
set -euo pipefail
docker start skillhub-postgres-1 skillhub-redis-1 skillhub-server-1 skillhub-web-1
