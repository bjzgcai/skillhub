#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VALIDATOR="$REPO_ROOT/scripts/validate-release-config.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

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

assert_contains ".env.quickstart.example" "SKILLHUB_PUBLIC_BASE_URL=http://localhost"
assert_contains ".env.quickstart.example" "SESSION_COOKIE_SECURE=false"
assert_contains ".env.quickstart.example" "SKILLHUB_STORAGE_PROVIDER=local"
assert_contains ".env.quickstart.example" "SKILLHUB_AUTH_DINGTALK_ENABLED=false"
assert_contains ".env.release.example" "SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com"
assert_contains ".env.release.example" "SESSION_COOKIE_SECURE=true"
assert_contains ".env.release.example" "SKILLHUB_STORAGE_PROVIDER=s3"
assert_not_contains ".env.release.example" "SKILLHUB_PUBLIC_BASE_URL=http://localhost"
assert_not_contains ".env.release.example" "SESSION_COOKIE_SECURE=false"

cat > "$TMP_DIR/local.env" <<'EOF'
SKILLHUB_PUBLIC_BASE_URL=http://localhost
SKILLHUB_API_UPSTREAM=http://server:8080
POSTGRES_DB=skillhub
POSTGRES_USER=skillhub
POSTGRES_PASSWORD=local-postgres-secret
POSTGRES_BIND_ADDRESS=127.0.0.1
REDIS_BIND_ADDRESS=127.0.0.1
WEB_BIND_ADDRESS=127.0.0.1
SESSION_COOKIE_SECURE=true
SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=false
SKILLHUB_STORAGE_PROVIDER=local
SKILLHUB_STORAGE_S3_ENDPOINT=https://oss.example.invalid
SKILLHUB_STORAGE_S3_BUCKET=skillhub
SKILLHUB_STORAGE_S3_ACCESS_KEY=replace-me
SKILLHUB_STORAGE_S3_SECRET_KEY=replace-me
SKILLHUB_STORAGE_S3_REGION=us-east-1
BOOTSTRAP_ADMIN_ENABLED=false
SKILLHUB_AUTH_DINGTALK_ENABLED=false
SKILLHUB_WEB_AUTH_DINGTALK_ENABLED=false
EOF

sed -i 's/^SESSION_COOKIE_SECURE=true$/SESSION_COOKIE_SECURE=false/' "$TMP_DIR/local.env"
"$VALIDATOR" "$TMP_DIR/local.env" quickstart >/dev/null

sed \
  -e 's#^SKILLHUB_PUBLIC_BASE_URL=.*#SKILLHUB_PUBLIC_BASE_URL=https://skillhub.example.com#' \
  -e 's/^SESSION_COOKIE_SECURE=false$/SESSION_COOKIE_SECURE=true/' \
  -e 's/^SKILLHUB_STORAGE_PROVIDER=local$/SKILLHUB_STORAGE_PROVIDER=s3/' \
  -e 's/^SKILLHUB_STORAGE_S3_ACCESS_KEY=.*/SKILLHUB_STORAGE_S3_ACCESS_KEY=real-access-key/' \
  -e 's/^SKILLHUB_STORAGE_S3_SECRET_KEY=.*/SKILLHUB_STORAGE_S3_SECRET_KEY=real-secret-key/' \
  -e 's/^SKILLHUB_STORAGE_S3_ENDPOINT=.*/SKILLHUB_STORAGE_S3_ENDPOINT=https:\/\/oss.example.com/' \
  "$TMP_DIR/local.env" > "$TMP_DIR/production.env"
"$VALIDATOR" "$TMP_DIR/production.env" production >/dev/null

sed 's/^SESSION_COOKIE_SECURE=false$/SESSION_COOKIE_SECURE=true/' \
  "$TMP_DIR/local.env" > "$TMP_DIR/quickstart-secure.env"
if "$VALIDATOR" "$TMP_DIR/quickstart-secure.env" quickstart >/dev/null 2>&1; then
  echo 'expected Quickstart secure cookies to be rejected for HTTP' >&2
  exit 1
fi

sed 's/^SKILLHUB_STORAGE_PROVIDER=s3$/SKILLHUB_STORAGE_PROVIDER=local/' \
  "$TMP_DIR/production.env" > "$TMP_DIR/production-local-storage.env"
if "$VALIDATOR" "$TMP_DIR/production-local-storage.env" production >/dev/null 2>&1; then
  echo 'expected production local storage to be rejected' >&2
  exit 1
fi

sed 's/^SKILLHUB_STORAGE_PROVIDER=local$/SKILLHUB_STORAGE_PROVIDER=s3/' \
  "$TMP_DIR/local.env" > "$TMP_DIR/s3.env"
if "$VALIDATOR" "$TMP_DIR/s3.env" quickstart >/dev/null 2>&1; then
  echo 'expected S3 placeholders to be rejected' >&2
  exit 1
fi

sed 's/^SKILLHUB_STORAGE_PROVIDER=local$/SKILLHUB_STORAGE_PROVIDER=s3/; s/^SKILLHUB_STORAGE_S3_ACCESS_KEY=.*/SKILLHUB_STORAGE_S3_ACCESS_KEY=TODO_fill_real_access_key/; s/^SKILLHUB_STORAGE_S3_SECRET_KEY=.*/SKILLHUB_STORAGE_S3_SECRET_KEY=TODO_fill_real_secret_key/' \
  "$TMP_DIR/local.env" > "$TMP_DIR/todo-s3.env"
if "$VALIDATOR" "$TMP_DIR/todo-s3.env" quickstart >/dev/null 2>&1; then
  echo 'expected TODO S3 placeholders to be rejected' >&2
  exit 1
fi

echo 'validate-release-config tests passed'
