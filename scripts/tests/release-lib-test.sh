#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CALL_LOG="$(mktemp)"
DEPLOY_CID_FILE="/tmp/skillhub.deploy.unified-scanner.cid"

cleanup() {
  rm -f "$CALL_LOG" "$DEPLOY_CID_FILE"
}
trap cleanup EXIT

MOCK_CURRENT_IMAGE="skill-security-scanner:test"
MOCK_PACKAGE_SIZE="209715200"
MOCK_REPACKED_PACKAGE_SIZE="335544320"
MOCK_FILE_COUNT="20000"
MOCK_SINGLE_FILE_SIZE="20971520"
MOCK_UNCOMPRESSED_SIZE="314572800"
MOCK_MISSING_IMAGE=""

docker() {
  printf '%s\n' "$*" >> "$CALL_LOG"
  if [ "$1" = "image" ] && [ "$2" = "inspect" ] && [ "$3" = "$MOCK_MISSING_IMAGE" ]; then
    return 1
  fi
  if [ "$1" = "inspect" ] && [ "$2" = "skillhub-security-scanner-1" ]; then
    if [ "$4" = "{{.Config.Image}}" ]; then
      printf '%s\n' "$MOCK_CURRENT_IMAGE"
    else
      printf '%s\n' \
        "SCANNER_MAX_PACKAGE_SIZE_BYTES=$MOCK_PACKAGE_SIZE" \
        "SCANNER_MAX_REPACKED_PACKAGE_SIZE_BYTES=$MOCK_REPACKED_PACKAGE_SIZE" \
        "SCANNER_MAX_FILE_COUNT=$MOCK_FILE_COUNT" \
        "SCANNER_MAX_SINGLE_FILE_SIZE_BYTES=$MOCK_SINGLE_FILE_SIZE" \
        "SCANNER_MAX_UNCOMPRESSED_SIZE_BYTES=$MOCK_UNCOMPRESSED_SIZE"
    fi
  elif [ "$1" = "ps" ]; then
    printf '%s\n' "scanner-container-id"
  fi
}

assert_log_contains() {
  local expected="$1"
  grep -F -- "$expected" "$CALL_LOG" >/dev/null || {
    echo "expected docker call log to contain: $expected" >&2
    cat "$CALL_LOG" >&2
    exit 1
  }
}

assert_log_excludes() {
  local unexpected="$1"
  if grep -F -- "$unexpected" "$CALL_LOG" >/dev/null; then
    echo "expected docker call log to exclude: $unexpected" >&2
    cat "$CALL_LOG" >&2
    exit 1
  fi
}

# shellcheck disable=SC1091
. "$REPO_ROOT/ops/release-lib.sh"

SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED=true
SKILLHUB_SECURITY_SCANNER_IMAGE=skill-security-scanner
SKILLHUB_SECURITY_SCANNER_TAG=test
SCANNER_MAX_PACKAGE_SIZE_BYTES=209715200
SCANNER_MAX_REPACKED_PACKAGE_SIZE_BYTES=335544320
SCANNER_MAX_FILE_COUNT=20000
SCANNER_MAX_SINGLE_FILE_SIZE_BYTES=20971520
SCANNER_MAX_UNCOMPRESSED_SIZE_BYTES=314572800

: > "$CALL_LOG"
run_unified_scanner_container
assert_log_contains "-e SCANNER_MAX_PACKAGE_SIZE_BYTES=209715200"
assert_log_contains "-e SCANNER_MAX_REPACKED_PACKAGE_SIZE_BYTES=335544320"
assert_log_contains "-e SCANNER_MAX_FILE_COUNT=20000"
assert_log_contains "-e SCANNER_MAX_SINGLE_FILE_SIZE_BYTES=20971520"
assert_log_contains "-e SCANNER_MAX_UNCOMPRESSED_SIZE_BYTES=314572800"

# A rollback manifest can select a different image from the shared environment.
: > "$CALL_LOG"
ensure_unified_scanner_container skill-security-scanner:rollback
assert_log_contains "image inspect skill-security-scanner:rollback"
assert_log_contains "run -d"
assert_log_contains "skill-security-scanner:rollback"

# A missing target must not remove the healthy scanner.
: > "$CALL_LOG"
MOCK_MISSING_IMAGE=skill-security-scanner:missing
if ensure_unified_scanner_container "$MOCK_MISSING_IMAGE"; then
  echo 'expected missing scanner image to fail' >&2
  exit 1
fi
assert_log_excludes "rm -f skillhub-security-scanner-1"
assert_log_excludes "run -d"
MOCK_MISSING_IMAGE=""

env_file="$(mktemp)"
printf 'SKILLHUB_SECURITY_SCANNER_TAG=old\n' > "$env_file"
update_release_env_value "$env_file" SKILLHUB_SECURITY_SCANNER_TAG new
update_release_env_value "$env_file" SKILLHUB_SERVER_TAG server-new
grep -qx 'SKILLHUB_SECURITY_SCANNER_TAG=new' "$env_file"
grep -qx 'SKILLHUB_SERVER_TAG=server-new' "$env_file"
rm -f "$env_file"

: > "$CALL_LOG"
ensure_unified_scanner_container
assert_log_excludes "rm -f skillhub-security-scanner-1"
assert_log_excludes "run -d"

: > "$CALL_LOG"
MOCK_FILE_COUNT=19999
ensure_unified_scanner_container
assert_log_contains "rm -f skillhub-security-scanner-1"
assert_log_contains "run -d"
assert_log_contains "-e SCANNER_MAX_FILE_COUNT=20000"
