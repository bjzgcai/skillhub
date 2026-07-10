#!/usr/bin/env bash
# verify-storage.sh — Pre-deploy & post-deploy storage validation for SkillHub server.
#
# Checks:
#   Pre-deploy:  env vars + volume existence + volume data
#   Post-deploy: container env + volume mount + writable + content + bundle path + download
#
# Usage:
#   verify-storage.sh --pre [--env-file <path>] [--allow-empty]
#   verify-storage.sh --post [--test-slug <slug>] [--allow-empty]
#   verify-storage.sh --all [--env-file <path>] [--test-slug <slug>]
#
# Exit codes: 0=pass, 1=storage misconfiguration, 2=invalid args

set -euo pipefail

BASE=/opt/skillhub
PHASE=""
ENV_FILE=""
ALLOW_EMPTY=0
TEST_SLUG="${VERIFY_STORAGE_TEST_SLUG:-weather}"
ADMIN_ENV="${ADMIN_ENV:-/home/ubuntu/.openclaw/workspace/.secrets/skillhub-admin.env}"

usage() {
  cat <<USAGE
Usage: $0 --pre|--post|--all [options]

  --pre                  Pre-deploy: check env vars and volume mount
  --post                 Post-deploy: check container storage + bundle access
  --all                  Run both pre and post
  --env-file <path>      Env file to check (pre-deploy)
  --allow-empty          Skip non-empty check (for first-time deploy)
  --test-slug <slug>     Skill slug for bundle path check (default: weather)
USAGE
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --pre) PHASE="pre"; shift ;;
    --post) PHASE="post"; shift ;;
    --all) PHASE="all"; shift ;;
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --allow-empty) ALLOW_EMPTY=1; shift ;;
    --test-slug) TEST_SLUG="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; usage ;;
  esac
done

[ -n "$PHASE" ] || usage

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

fail() { echo -e "${RED}❌ FAIL: $*${NC}" >&2; exit 1; }
pass() { echo -e "${GREEN}✅ PASS: $*${NC}"; }
warn() { echo -e "${YELLOW}⚠️  WARN: $*${NC}"; }

run_pre_deploy() {
  echo "=== Pre-deploy storage verification ==="

  local found=0

  # 1. Check STORAGE_BASE_PATH in env file
  if [ -n "$ENV_FILE" ] && [ -f "$ENV_FILE" ]; then
    if grep -q '^STORAGE_BASE_PATH=' "$ENV_FILE"; then
      val="$(grep '^STORAGE_BASE_PATH=' "$ENV_FILE" | cut -d= -f2-)"
      pass "STORAGE_BASE_PATH in env file: $val"
      found=1
    else
      warn "STORAGE_BASE_PATH NOT in env file ($ENV_FILE) — must be injected via -e flag"
    fi
  fi

  # 2. Check release-lib.sh injects it
  if grep -q 'STORAGE_BASE_PATH=/var/lib/skillhub/storage' "$BASE/ops/release-lib.sh" 2>/dev/null; then
    pass "STORAGE_BASE_PATH injected by release-lib.sh run_server_container()"
    found=1
  fi

  # 3. Check shared env
  if grep -q '^STORAGE_BASE_PATH=' "$BASE/shared/env.release" 2>/dev/null; then
    pass "STORAGE_BASE_PATH in shared/env.release"
    found=1
  fi

  if [ "$found" -eq 0 ]; then
    fail "STORAGE_BASE_PATH not found anywhere — container will fall back to /tmp/skillhub-storage"
  fi

  # 4. Volume exists
  local volume_name="${SKILLHUB_STORAGE_VOLUME:-skillhub_skillhub_storage}"
  if docker volume inspect "$volume_name" >/dev/null 2>&1; then
    pass "Docker volume exists: $volume_name"
  else
    fail "Docker volume not found: $volume_name"
  fi

  # 5. Volume has data (check via temporary container, not host path)
  if [ "$ALLOW_EMPTY" -eq 0 ]; then
    vol_check=$(docker run --rm -v "${volume_name}:/data" redis:7-alpine sh -c '
      skill_count=$(find /data/skills -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l)
      bundle_count=$(find /data/packages -name "bundle.zip" 2>/dev/null | wc -l)
      echo "${skill_count} ${bundle_count}"
    ' 2>/dev/null || echo "0 0")
    skill_count=$(echo "$vol_check" | awk '{print $1}')
    bundle_count=$(echo "$vol_check" | awk '{print $2}')
    if [ "$skill_count" -gt 0 ] && [ "$bundle_count" -gt 0 ]; then
      pass "Volume has data: ${skill_count} skill dirs, ${bundle_count} bundle.zip files"
    else
      fail "Volume is empty: ${skill_count} skills, ${bundle_count} bundles — use --allow-empty for first deploy"
    fi
  else
    warn "Skipping non-empty check (--allow-empty)"
  fi

  # 6. Compose template
  local compose_tpl="$BASE/releases/templates/compose.release.yml.tpl"
  if [ -f "$compose_tpl" ] && grep -q 'STORAGE_BASE_PATH' "$compose_tpl"; then
    pass "Compose template includes STORAGE_BASE_PATH"
  else
    warn "Compose template missing STORAGE_BASE_PATH — verify if using compose or manual docker run"
  fi

  echo "=== Pre-deploy verification complete ==="
}

run_post_deploy() {
  echo "=== Post-deploy storage verification ==="

  local server_cid
  server_cid="$(docker ps -qf name='^skillhub-server-1$' || true)"
  [ -n "$server_cid" ] || fail "skillhub-server-1 container is not running"

  # 1. STORAGE_BASE_PATH in running container
  env_val="$(docker inspect skillhub-server-1 --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | awk -F= '$1 == "STORAGE_BASE_PATH" {print $2; exit}')"
  if [ -z "$env_val" ]; then
    fail "STORAGE_BASE_PATH is NOT set in container — Java app defaults to /tmp/skillhub-storage"
  elif [ "$env_val" != "/var/lib/skillhub/storage" ]; then
    fail "STORAGE_BASE_PATH='$env_val' but expected '/var/lib/skillhub/storage'"
  else
    pass "STORAGE_BASE_PATH = /var/lib/skillhub/storage"
  fi

  # 2. Warn about wrong variable name
  wrong_val="$(docker inspect skillhub-server-1 --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | awk -F= '$1 == "SKILLHUB_STORAGE_LOCAL_BASE_DIR" {print $2; exit}')"
  if [ -n "$wrong_val" ]; then
    warn "SKILLHUB_STORAGE_LOCAL_BASE_DIR='$wrong_val' — does NOT map to any Spring property, has no effect"
  fi

  # 3. Volume mounted
  mount_found=0
  while IFS= read -r line; do
    if echo "$line" | grep -q 'skillhub_storage.*:/var/lib/skillhub/storage'; then
      mount_found=1; break
    fi
  done < <(docker inspect skillhub-server-1 --format '{{range .Mounts}}{{.Name}}:{{.Destination}}{{println}}{{end}}' 2>/dev/null)
  if [ "$mount_found" -eq 1 ]; then
    pass "Storage volume mounted to /var/lib/skillhub/storage"
  else
    fail "Storage volume NOT mounted — container cannot access skill bundles"
  fi

  # 4. Writable probe
  if docker exec skillhub-server-1 sh -lc '
    set -eu
    base=/var/lib/skillhub/storage
    suffix=.verify-$(date +%s)-$$
    for parent in skills packages; do
      probe="$base/$parent/$suffix"
      mkdir "$probe"; rmdir "$probe"
    done
  ' 2>/dev/null; then
    pass "Storage is writable (mkdir/rmdir probe)"
  else
    fail "Storage write permission check failed"
  fi

  # 5. Content visible from inside container
  inner_skill_count="$(docker exec skillhub-server-1 sh -lc 'ls -1 /var/lib/skillhub/storage/skills/ 2>/dev/null | wc -l' 2>/dev/null || echo 0)"
  inner_bundle_count="$(docker exec skillhub-server-1 sh -lc 'find /var/lib/skillhub/storage/packages -name "bundle.zip" 2>/dev/null | wc -l' 2>/dev/null || echo 0)"
  if [ "$inner_skill_count" -gt 0 ] && [ "$inner_bundle_count" -gt 0 ]; then
    pass "Container sees: ${inner_skill_count} skill dirs, ${inner_bundle_count} bundle.zip files"
  elif [ "$ALLOW_EMPTY" -eq 1 ]; then
    warn "Storage appears empty (allowed via --allow-empty)"
  else
    fail "Container sees empty storage: ${inner_skill_count} skills, ${inner_bundle_count} bundles — path mismatch likely"
  fi

  # 6. Verify known skill's bundle.zip at exact path Java expects
  test_skill_id="$(docker exec skillhub-postgres-1 psql -U skillhub -d skillhub -tAc \
    "SELECT id FROM skill WHERE slug='${TEST_SLUG}' LIMIT 1" 2>/dev/null | tr -d '[:space:]' || echo '')"
  if [ -n "$test_skill_id" ]; then
    test_version_id="$(docker exec skillhub-postgres-1 psql -U skillhub -d skillhub -tAc \
      "SELECT id FROM skill_version WHERE skill_id=${test_skill_id} ORDER BY created_at DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]' || echo '')"
    if [ -n "$test_version_id" ]; then
      bundle_path="packages/${test_skill_id}/${test_version_id}/bundle.zip"
      if docker exec skillhub-server-1 test -f "/var/lib/skillhub/storage/${bundle_path}"; then
        pass "Bundle exists: ${bundle_path}"
      else
        fail "Bundle NOT found: ${bundle_path} — STORAGE_BASE_PATH mismatch or storage corruption"
      fi
    else
      warn "No version found for skill '${TEST_SLUG}' — skipping bundle path check"
    fi
  else
    warn "Skill '${TEST_SLUG}' not found in DB — skipping bundle path check"
  fi

  # 7. Skill download test (best-effort, non-blocking on auth)
  echo "--- Testing skill download ---"
  local download_ok=0

  if command -v clawhub >/dev/null 2>&1; then
    if clawhub install "$TEST_SLUG" --registry https://skills.zgci.org --force 2>&1 | grep -qi 'OK.*Installed'; then
      download_ok=1
    fi
  elif command -v npx >/dev/null 2>&1; then
    if npx -y clawhub install "$TEST_SLUG" --registry https://skills.zgci.org --force 2>&1 | grep -qi 'OK.*Installed'; then
      download_ok=1
    fi
  fi

  if [ "$download_ok" -eq 1 ]; then
    pass "clawhub install succeeded for $TEST_SLUG"
  else
    # Try admin API for download test
    local cookie_jar admin_pass=""
    cookie_jar="$(mktemp)"

    for f in "$ADMIN_ENV" /home/ubuntu/.openclaw/workspace/.secrets/skillhub-admin.env; do
      if [ -f "$f" ]; then
        # shellcheck disable=SC1090
        . "$f" 2>/dev/null || true
        admin_pass="${SKILLHUB_ADMIN_PASSWORD:-}"
        [ -n "$admin_pass" ] && break
      fi
    done

    if [ -n "$admin_pass" ]; then
      login_code="$(curl -s -o /dev/null -w '%{http_code}' -c "$cookie_jar" -b "$cookie_jar" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"admin\",\"password\":\"$admin_pass\"}" \
        "http://127.0.0.1:8080/api/v1/auth/local/login" 2>/dev/null || echo 000)"
      if [ "$login_code" = "200" ]; then
        xsrf_token="$(awk '$6=="XSRF-TOKEN"{print $7}' "$cookie_jar" | tail -1)"
        dl_code="$(curl -s -o /dev/null -w '%{http_code}' -b "$cookie_jar" \
          -H "X-XSRF-TOKEN: $xsrf_token" \
          "http://127.0.0.1:8080/api/v1/skills/${TEST_SLUG}/versions/latest/download" 2>/dev/null || echo 000)"
        if [ "$dl_code" = "200" ]; then
          pass "Skill download API returned 200 for ${TEST_SLUG}"
        else
          fail "Skill download API returned ${dl_code} for ${TEST_SLUG} — bundle may not be accessible"
        fi
      else
        warn "Admin login failed (HTTP $login_code) — bundle path check (#6) is authoritative"
      fi
    else
      warn "No admin credentials available — bundle path check (#6) is authoritative"
    fi
    rm -f "$cookie_jar"
  fi

  echo "=== Post-deploy verification complete ==="
}

case "$PHASE" in
  pre) run_pre_deploy ;;
  post) run_post_deploy ;;
  all) run_pre_deploy; echo ""; run_post_deploy ;;
esac

echo "✅ storage verify ok"
