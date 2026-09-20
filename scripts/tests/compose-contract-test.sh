#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

assert_contains() {
  local file="$1"
  local expected="$2"
  grep -F -- "$expected" "$REPO_ROOT/$file" >/dev/null || {
    echo "expected $file to contain: $expected" >&2
    exit 1
  }
}

assert_not_contains() {
  local file="$1"
  local unexpected="$2"
  if grep -F -- "$unexpected" "$REPO_ROOT/$file" >/dev/null; then
    echo "expected $file not to contain: $unexpected" >&2
    exit 1
  fi
}

DEV_COMPOSE="$REPO_ROOT/docker-compose.yml"
RELEASE_COMPOSE="$REPO_ROOT/compose.release.yml"
RELEASE_TEMPLATE="$REPO_ROOT/ops/templates/compose.release.yml.tpl"
RELEASE_LIB="$REPO_ROOT/ops/release-lib.sh"
DEPLOY_RELEASE="$REPO_ROOT/ops/deploy-release.sh"

# Quickstart and production must have visibly different defaults. The runtime
# script is the Quickstart entrypoint; compose.release.yml remains the shared
# application topology used by both paths.
assert_contains ".env.quickstart.example" "SKILLHUB_PUBLIC_BASE_URL=http://localhost"
assert_contains ".env.quickstart.example" "SESSION_COOKIE_SECURE=false"
assert_contains ".env.quickstart.example" "SKILLHUB_STORAGE_PROVIDER=local"
assert_contains ".env.quickstart.example" "SKILLHUB_SERVER_IMAGE=ghcr.io/iflytek/skillhub-server"
assert_contains ".env.quickstart.example" "SKILLHUB_PULL_POLICY=missing"
assert_contains ".env.quickstart.example" "API_BIND_ADDRESS=127.0.0.1"
assert_contains ".env.release.example" "SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com"
assert_contains ".env.release.example" "SESSION_COOKIE_SECURE=true"
assert_contains ".env.release.example" "SKILLHUB_STORAGE_PROVIDER=s3"
assert_not_contains ".env.release.example" "SKILLHUB_PUBLIC_BASE_URL=http://localhost"
assert_not_contains ".env.release.example" "SESSION_COOKIE_SECURE=false"
assert_contains "scripts/runtime.sh" 'ENV_EXAMPLE_FILE="$SKILLHUB_HOME/.env.quickstart.example"'
assert_contains "scripts/runtime.sh" 'ENV_FILE="$SKILLHUB_HOME/.env.quickstart"'
assert_contains "scripts/runtime.sh" 'LEGACY_ENV_FILE="$SKILLHUB_HOME/.env.release"'
assert_contains "scripts/runtime.sh" 'COMPOSE_PROFILES="$profiles"'
assert_contains "scripts/runtime.sh" 'RUNTIME_VALIDATION_MODE="production"'

# The development file must remain dependency-only and loopback-bound.
assert_contains "docker-compose.yml" "# Local development dependencies only."
assert_contains "docker-compose.yml" '127.0.0.1:5432:5432'
assert_contains "docker-compose.yml" '127.0.0.1:6379:6379'
assert_contains "docker-compose.yml" '127.0.0.1:9000:9000'
assert_not_contains "docker-compose.yml" '  server:'
assert_not_contains "docker-compose.yml" '  web:'

# The release file must contain the complete application stack and require the
# security-sensitive values instead of silently falling back to dev defaults.
assert_contains "compose.release.yml" $'\n  server:'
assert_contains "compose.release.yml" $'\n  web:'
assert_contains "compose.release.yml" 'POSTGRES_PASSWORD must be set to a unique production secret'
assert_contains "compose.release.yml" 'SESSION_COOKIE_SECURE must be explicitly set'
assert_contains "compose.release.yml" 'SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED must be explicitly set'
assert_contains "compose.release.yml" 'BOOTSTRAP_ADMIN_ENABLED must be explicitly set'
assert_contains "compose.release.yml" 'BOOTSTRAP_ADMIN_PASSWORD: ${BOOTSTRAP_ADMIN_PASSWORD:-}'
assert_contains "compose.release.yml" 'pull_policy: ${SKILLHUB_PULL_POLICY:-missing}'
assert_contains "compose.release.yml" '"${API_BIND_ADDRESS:-127.0.0.1}:${API_PORT:-8080}:8080"'
assert_contains "server/skillhub-app/src/main/resources/application.yml" 'registration-enabled: ${SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED:false}'
assert_contains "server/skillhub-app/src/main/resources/application-local.yml" 'registration-enabled: ${SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED:true}'

# The production release snapshot must expose the same security contract.
assert_contains "ops/templates/compose.release.yml.tpl" 'SESSION_COOKIE_SECURE: ${SESSION_COOKIE_SECURE:-true}'
assert_contains "ops/templates/compose.release.yml.tpl" 'SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED: ${SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED:-false}'
assert_contains "ops/templates/compose.release.yml.tpl" 'BOOTSTRAP_ADMIN_ENABLED: ${BOOTSTRAP_ADMIN_ENABLED:?missing}'
assert_contains "ops/templates/compose.release.yml.tpl" 'BOOTSTRAP_ADMIN_PASSWORD: ${BOOTSTRAP_ADMIN_PASSWORD:-}'
assert_contains "ops/templates/compose.release.yml.tpl" 'SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}'
assert_contains "ops/templates/compose.release.yml.tpl" 'SKILLHUB_PUBLIC_BASE_URL: ${SKILLHUB_PUBLIC_BASE_URL:?missing}'
assert_contains "ops/templates/compose.release.yml.tpl" 'SKILLHUB_API_UPSTREAM: ${SKILLHUB_API_UPSTREAM:?missing}'

# The current production path is docker run, so its security switches must not
# drift from the release contract. deploy-release.sh also blocks unsafe apply.
assert_contains "ops/release-lib.sh" '-e SESSION_COOKIE_SECURE="${SESSION_COOKIE_SECURE:?SESSION_COOKIE_SECURE must be set}"'
assert_contains "ops/release-lib.sh" '-e SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED="${SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED:?SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED must be set}"'
assert_contains "ops/release-lib.sh" 'POSTGRES_PASSWORD must be set to a unique production secret'
assert_contains "ops/release-lib.sh" '--env-file "$env_file"'
assert_contains "ops/deploy-release.sh" 'SESSION_COOKIE_SECURE:-'
assert_contains "ops/deploy-release.sh" 'SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED:-'
assert_contains "ops/deploy-release.sh" 'SESSION_COOKIE_SECURE must be true for production server deploys'
assert_contains "ops/deploy-release.sh" 'SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED must be false for production server deploys'
assert_not_contains "ops/verify-server-release.sh" '/api/v1/auth/local/register'

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  dev_services="$(docker compose -f "$DEV_COMPOSE" config --services)"
  for service in gitleaks-scanner skill-scanner postgres redis minio; do
    printf '%s\n' "$dev_services" | grep -qx "$service" || {
      echo "development Compose is missing service: $service" >&2
      exit 1
    }
  done
  for service in server web; do
    if printf '%s\n' "$dev_services" | grep -qx "$service"; then
      echo "development Compose must not include application service: $service" >&2
      exit 1
    fi
  done

  env_file="$(mktemp)"
  cleanup() { rm -f "$env_file"; }
  trap cleanup EXIT
  cat > "$env_file" <<'EOF'
SKILLHUB_SERVER_IMAGE=skillhub-server
SKILLHUB_SERVER_TAG=test
SKILLHUB_WEB_IMAGE=skillhub-web
SKILLHUB_WEB_TAG=test
SKILLHUB_PULL_POLICY=missing
POSTGRES_PASSWORD=contract-test-postgres
SESSION_COOKIE_SECURE=true
SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=false
SKILLHUB_PUBLIC_BASE_URL=http://localhost
API_BIND_ADDRESS=127.0.0.1
SKILLHUB_STORAGE_PROVIDER=local
BOOTSTRAP_ADMIN_PASSWORD=contract-test-admin
BOOTSTRAP_ADMIN_ENABLED=false
EOF
  release_services="$(docker compose --env-file "$env_file" -f "$RELEASE_COMPOSE" config --services)"
  for service in postgres redis server web; do
    printf '%s\n' "$release_services" | grep -qx "$service" || {
      echo "release Compose is missing service: $service" >&2
      exit 1
    }
  done

  unified_services="$(COMPOSE_PROFILES=unified-scan docker compose --env-file "$env_file" -f "$RELEASE_COMPOSE" config --services)"
  printf '%s\n' "$unified_services" | grep -qx "security-scanner" || {
    echo "release Compose must expose security-scanner when unified-scan profile is enabled" >&2
    exit 1
  }
fi

echo "Compose and production container contract checks passed."
