#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  ops/recommend-external-skill.sh <bundle-url> [options]

Downloads an external skill bundle, publishes it into SkillHub, verifies it is
reachable from local storage, then adds/updates the operation recommendation by
namespace + slug.

Options:
  --base-url <url>      SkillHub server base URL (default: http://127.0.0.1:8080)
  --namespace <slug>    Target namespace for publish (default: global)
  --visibility <value>  Publish visibility, PUBLIC or PRIVATE (default: PUBLIC)
  --username <name>     Local admin username (or SKILLHUB_ADMIN_USERNAME)
  --password <value>    Local admin password (or SKILLHUB_ADMIN_PASSWORD)
  --title <text>        Recommendation title, also used as publish displayName
  --summary <text>      Recommendation summary, also used as publish summary
  --reason <text>       Recommendation reason
  --badge <text>        Recommendation badge
  --priority <number>   Recommendation priority (default: 0)
  --start-at <instant>  Optional recommendation start time, ISO-8601
  --end-at <instant>    Optional recommendation end time, ISO-8601
  --source-registry <id>
                       Mark the published version as mirrored from a registry, e.g. clawhub
  --source-namespace <slug>
                       Source namespace/owner handle, e.g. mineru-extract
  --source-slug <slug>  Source skill slug. Defaults to the published slug when source registry is set
  --source-version <ver>
                       Source version. Defaults to the published version when source registry is set
  --source-canonical-slug <slug>
                       Source canonical slug. Defaults to namespace--slug, or slug when namespace is empty/global
  --source-download-url <url>
                       Source bundle URL. Defaults to the input bundle URL when source registry is set
  --help                Show this help

Environment:
  SKILLHUB_ADMIN_USERNAME / SKILLHUB_ADMIN_PASSWORD can provide credentials.
  POSTGRES_PASSWORD can provide the production DB password for source metadata writes.
  If POSTGRES_PASSWORD is not set, /opt/skillhub/shared/secrets.env is used when readable.

Example:
  SKILLHUB_ADMIN_USERNAME=admin SKILLHUB_ADMIN_PASSWORD='***' \
    ops/recommend-external-skill.sh https://example.com/skill.zip \
      --base-url http://127.0.0.1:18081 \
      --namespace global \
      --title 'Recommended skill' \
      --reason 'Useful for daily workflows' \
      --badge '推荐' \
      --priority 100 \
      --source-registry clawhub \
      --source-namespace upstream-owner \
      --source-slug upstream-skill \
      --source-version 1.2.3
USAGE
}

json_get() {
  local expr="$1"
  python3 -c 'import json,sys
payload=json.load(sys.stdin)
expr=sys.argv[1]
value=payload
for part in expr.split("."):
    value=value[part]
if value is None:
    raise SystemExit(1)
print(value)' "$expr"
}

json_body() {
  python3 - "$@" <<'PY'
import json, sys
pairs = sys.argv[1:]
out = {}
for pair in pairs:
    key, value = pair.split('=', 1)
    if value == '':
        continue
    if key == 'priority':
        out[key] = int(value)
    else:
        out[key] = value
print(json.dumps(out, ensure_ascii=False))
PY
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

source_default_canonical_slug() {
  local src_namespace="$1"
  local src_slug="$2"
  if [[ -z "$src_namespace" || "$src_namespace" == "global" ]]; then
    printf '%s' "$src_slug"
  else
    printf '%s--%s' "$src_namespace" "$src_slug"
  fi
}


bundle_metadata_value() {
  local bundle="$1"
  local key="$2"
  python3 - "$bundle" "$key" <<'PY'
import json, sys, zipfile

bundle, key = sys.argv[1:]
try:
    with zipfile.ZipFile(bundle) as archive:
        try:
            payload = json.loads(archive.read('_meta.json'))
        except KeyError:
            raise SystemExit(0)
except Exception:
    raise SystemExit(0)
value = payload.get(key)
if value is not None:
    print(value)
PY
}

url_query_value() {
  local url="$1"
  local key="$2"
  python3 - "$url" "$key" <<'PY'
import sys, urllib.parse

url, key = sys.argv[1:]
parsed = urllib.parse.urlparse(url)
values = urllib.parse.parse_qs(parsed.query).get(key, [])
if values:
    print(values[0])
PY
}

upsert_remote_mirror_record() {
  local skill_id="$1"
  local version="$2"
  local src_registry="$3"
  local src_canonical_slug="$4"
  local src_namespace="$5"
  local src_slug="$6"
  local requested_version="$7"
  local remote_version="$8"
  local bundle_sha256="$9"
  local download_url="${10}"

  require_cmd docker

  local secrets_file="/opt/skillhub/shared/secrets.env"
  local pg_password="${POSTGRES_PASSWORD:-}"
  if [[ -z "$pg_password" && -r "$secrets_file" ]]; then
    # shellcheck disable=SC1090
    pg_password="$(set -a; . "$secrets_file"; printf '%s' "${POSTGRES_PASSWORD:-}")"
  fi
  if [[ -z "$pg_password" ]]; then
    echo "Missing POSTGRES_PASSWORD. Set it or make /opt/skillhub/shared/secrets.env readable." >&2
    exit 1
  fi

  local sql
  sql="$(python3 - "$skill_id" "$version" "$src_registry" "$src_canonical_slug" "$src_namespace" "$src_slug" "$requested_version" "$remote_version" "$bundle_sha256" "$download_url" <<'PY'
import sys

skill_id, version, src_registry, src_canonical_slug, src_namespace, src_slug, requested_version, remote_version, bundle_sha256, download_url = sys.argv[1:]

def q(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"

print(f"""
WITH target_version AS (
  SELECT id AS skill_version_id, skill_id
  FROM skill_version
  WHERE skill_id = {int(skill_id)} AND version = {q(version)}
  ORDER BY id DESC
  LIMIT 1
)
INSERT INTO remote_mirror_record (
  skill_id,
  skill_version_id,
  source_registry,
  source_canonical_slug,
  source_namespace,
  source_slug,
  requested_version,
  remote_version,
  bundle_sha256,
  download_url
)
SELECT
  skill_id,
  skill_version_id,
  {q(src_registry)},
  {q(src_canonical_slug)},
  {q(src_namespace)},
  {q(src_slug)},
  {q(requested_version)},
  {q(remote_version)},
  {q(bundle_sha256)},
  {q(download_url)}
FROM target_version
ON CONFLICT (skill_version_id) DO UPDATE SET
  source_registry = EXCLUDED.source_registry,
  source_canonical_slug = EXCLUDED.source_canonical_slug,
  source_namespace = EXCLUDED.source_namespace,
  source_slug = EXCLUDED.source_slug,
  requested_version = EXCLUDED.requested_version,
  remote_version = EXCLUDED.remote_version,
  bundle_sha256 = EXCLUDED.bundle_sha256,
  download_url = EXCLUDED.download_url;
""")
PY
)"

  local affected
  affected="$(docker exec -i -e PGPASSWORD="$pg_password" skillhub-postgres-1 \
    psql -v ON_ERROR_STOP=1 -U skillhub -d skillhub -Atc "$sql")"
  if [[ "$affected" != "INSERT 0 1" ]]; then
    echo "Remote mirror record was not written for skill_id=$skill_id version=$version (psql: $affected)" >&2
    exit 1
  fi
}

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 2
fi

bundle_url=""
base_url="${SKILLHUB_BASE_URL:-http://127.0.0.1:8080}"
namespace="global"
visibility="PUBLIC"
username="${SKILLHUB_ADMIN_USERNAME:-}"
password="${SKILLHUB_ADMIN_PASSWORD:-}"
title=""
summary=""
reason=""
badge=""
priority="0"
start_at=""
end_at=""
source_registry=""
source_namespace=""
source_slug=""
source_version=""
source_canonical_slug=""
source_download_url=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h)
      usage
      exit 0
      ;;
    --base-url)
      base_url="${2:?Missing value for --base-url}"
      shift 2
      ;;
    --namespace)
      namespace="${2:?Missing value for --namespace}"
      shift 2
      ;;
    --visibility)
      visibility="${2:?Missing value for --visibility}"
      shift 2
      ;;
    --username)
      username="${2:?Missing value for --username}"
      shift 2
      ;;
    --password)
      password="${2:?Missing value for --password}"
      shift 2
      ;;
    --title)
      title="${2:?Missing value for --title}"
      shift 2
      ;;
    --summary)
      summary="${2:?Missing value for --summary}"
      shift 2
      ;;
    --reason)
      reason="${2:?Missing value for --reason}"
      shift 2
      ;;
    --badge)
      badge="${2:?Missing value for --badge}"
      shift 2
      ;;
    --priority)
      priority="${2:?Missing value for --priority}"
      shift 2
      ;;
    --start-at)
      start_at="${2:?Missing value for --start-at}"
      shift 2
      ;;
    --end-at)
      end_at="${2:?Missing value for --end-at}"
      shift 2
      ;;
    --source-registry)
      source_registry="${2:?Missing value for --source-registry}"
      shift 2
      ;;
    --source-namespace)
      source_namespace="${2:?Missing value for --source-namespace}"
      shift 2
      ;;
    --source-slug)
      source_slug="${2:?Missing value for --source-slug}"
      shift 2
      ;;
    --source-version)
      source_version="${2:?Missing value for --source-version}"
      shift 2
      ;;
    --source-canonical-slug)
      source_canonical_slug="${2:?Missing value for --source-canonical-slug}"
      shift 2
      ;;
    --source-download-url)
      source_download_url="${2:?Missing value for --source-download-url}"
      shift 2
      ;;
    --*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      if [[ -n "$bundle_url" ]]; then
        echo "Unexpected extra argument: $1" >&2
        usage >&2
        exit 2
      fi
      bundle_url="$1"
      shift
      ;;
  esac
done

if [[ -z "$bundle_url" ]]; then
  echo "Missing bundle URL" >&2
  usage >&2
  exit 2
fi
if [[ -z "$username" || -z "$password" ]]; then
  echo "Missing admin credentials. Use --username/--password or SKILLHUB_ADMIN_USERNAME/SKILLHUB_ADMIN_PASSWORD." >&2
  exit 2
fi
if [[ ! "$priority" =~ ^-?[0-9]+$ ]]; then
  echo "--priority must be an integer" >&2
  exit 2
fi

require_cmd curl
require_cmd python3

base_url="${base_url%/}"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
cookie_jar="$tmpdir/cookies.txt"
bundle_path="$tmpdir/bundle.zip"

printf 'Downloading bundle...\n' >&2
curl -fL --retry 3 --retry-delay 2 --connect-timeout 10 -o "$bundle_path" "$bundle_url"
if [[ ! -s "$bundle_path" ]]; then
  echo "Downloaded bundle is empty" >&2
  exit 1
fi

printf 'Logging in...\n' >&2
login_body="$(json_body "username=$username" "password=$password")"
login_response="$(curl -fsS -c "$cookie_jar" -b "$cookie_jar" \
  -H 'Content-Type: application/json' \
  -d "$login_body" \
  "$base_url/api/v1/auth/local/login")"
login_code="$(printf '%s' "$login_response" | json_get code)"
if [[ "$login_code" != "0" ]]; then
  echo "Login failed: $login_response" >&2
  exit 1
fi
xsrf_token="$(awk '$6=="XSRF-TOKEN"{print $7}' "$cookie_jar" | tail -1)"
if [[ -z "$xsrf_token" ]]; then
  echo "Login did not return XSRF-TOKEN" >&2
  exit 1
fi

printf 'Publishing bundle into namespace %s...\n' "$namespace" >&2
publish_args=(
  -fsS
  -c "$cookie_jar"
  -b "$cookie_jar"
  -H "X-XSRF-TOKEN: $xsrf_token"
  -F "file=@${bundle_path}"
  -F "visibility=${visibility}"
)
if [[ -n "$title" ]]; then
  publish_args+=(-F "displayName=${title}")
fi
if [[ -n "$summary" ]]; then
  publish_args+=(-F "summary=${summary}")
fi
publish_response="$(curl "${publish_args[@]}" "$base_url/api/v1/skills/$namespace/publish")"
publish_code="$(printf '%s' "$publish_response" | json_get code)"
if [[ "$publish_code" != "0" ]]; then
  echo "Publish failed: $publish_response" >&2
  exit 1
fi
published_namespace="$(printf '%s' "$publish_response" | json_get data.namespace)"
published_slug="$(printf '%s' "$publish_response" | json_get data.slug)"
published_version="$(printf '%s' "$publish_response" | json_get data.version)"
published_skill_id="$(printf '%s' "$publish_response" | json_get data.skillId)"
published_status="$(printf '%s' "$publish_response" | json_get data.status)"
if [[ "$published_status" != "PUBLISHED" ]]; then
  echo "Publish completed but version is not PUBLISHED: $published_status" >&2
  echo "$publish_response" >&2
  exit 1
fi

if [[ -n "$source_registry" ]]; then
  inferred_source_slug="$(bundle_metadata_value "$bundle_path" slug || true)"
  inferred_source_version="$(bundle_metadata_value "$bundle_path" version || true)"
  if [[ -z "$inferred_source_slug" ]]; then
    inferred_source_slug="$(url_query_value "$bundle_url" slug || true)"
  fi
  if [[ -z "$inferred_source_version" ]]; then
    inferred_source_version="$(url_query_value "$bundle_url" version || true)"
  fi
  if [[ -z "$inferred_source_version" ]]; then
    inferred_source_version="$(url_query_value "$bundle_url" tag || true)"
  fi

  source_namespace="${source_namespace:-global}"
  source_slug="${source_slug:-${inferred_source_slug:-$published_slug}}"
  source_version="${source_version:-${inferred_source_version:-$published_version}}"
  source_download_url="${source_download_url:-$bundle_url}"
  if [[ -z "$source_canonical_slug" ]]; then
    source_canonical_slug="$(source_default_canonical_slug "$source_namespace" "$source_slug")"
  fi
  bundle_sha256="$(python3 - "$bundle_path" <<'PY'
import hashlib, pathlib, sys
print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"

  printf 'Recording source mirror metadata: %s/%s@%s from %s...\n' "$source_namespace" "$source_slug" "$source_version" "$source_registry" >&2
  upsert_remote_mirror_record \
    "$published_skill_id" \
    "$published_version" \
    "$source_registry" \
    "$source_canonical_slug" \
    "$source_namespace" \
    "$source_slug" \
    "$source_version" \
    "$source_version" \
    "$bundle_sha256" \
    "$source_download_url"
fi

printf 'Verifying local download for %s/%s@%s...\n' "$published_namespace" "$published_slug" "$published_version" >&2
if ! curl -fsS -L -c "$cookie_jar" -b "$cookie_jar" \
  -o /dev/null \
  "$base_url/api/web/skills/$published_namespace/$published_slug/versions/$published_version/download"; then
  echo "Published skill is not downloadable; recommendation was not created." >&2
  exit 1
fi

recommend_body="$(json_body \
  "title=$title" \
  "summary=$summary" \
  "reason=$reason" \
  "badge=$badge" \
  "priority=$priority" \
  "startAt=$start_at" \
  "endAt=$end_at")"

printf 'Adding recommendation for %s/%s...\n' "$published_namespace" "$published_slug" >&2
recommend_response="$(curl -fsS -c "$cookie_jar" -b "$cookie_jar" \
  -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $xsrf_token" \
  -d "$recommend_body" \
  "$base_url/api/v1/admin/recommendations/$published_namespace/$published_slug")"
recommend_code="$(printf '%s' "$recommend_response" | json_get code)"
if [[ "$recommend_code" != "0" ]]; then
  echo "Recommendation failed: $recommend_response" >&2
  exit 1
fi

python3 - <<'PY' "$publish_response" "$recommend_response" "$source_registry" "$source_canonical_slug"
import json, sys
publish = json.loads(sys.argv[1])['data']
recommend = json.loads(sys.argv[2])['data']
source_registry = sys.argv[3]
source_canonical_slug = sys.argv[4]
out = {
    'namespace': publish['namespace'],
    'slug': publish['slug'],
    'version': publish['version'],
    'publishStatus': publish['status'],
    'recommendationStatus': recommend['status'],
    'cacheStatus': recommend['cacheStatus'],
    'title': recommend.get('title'),
}
if source_registry:
    out['sourceRegistry'] = source_registry
    out['sourceCanonicalSlug'] = source_canonical_slug
print(json.dumps(out, ensure_ascii=False, indent=2))
PY
