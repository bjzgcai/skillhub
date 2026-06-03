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
  --help                Show this help

Environment:
  SKILLHUB_ADMIN_USERNAME / SKILLHUB_ADMIN_PASSWORD can provide credentials.

Example:
  SKILLHUB_ADMIN_USERNAME=admin SKILLHUB_ADMIN_PASSWORD='***' \
    ops/recommend-external-skill.sh https://example.com/skill.zip \
      --base-url http://127.0.0.1:18081 \
      --namespace global \
      --title 'Recommended skill' \
      --reason 'Useful for daily workflows' \
      --badge '推荐' \
      --priority 100
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
published_status="$(printf '%s' "$publish_response" | json_get data.status)"
if [[ "$published_status" != "PUBLISHED" ]]; then
  echo "Publish completed but version is not PUBLISHED: $published_status" >&2
  echo "$publish_response" >&2
  exit 1
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

python3 - <<'PY' "$publish_response" "$recommend_response"
import json, sys
publish = json.loads(sys.argv[1])['data']
recommend = json.loads(sys.argv[2])['data']
print(json.dumps({
    'namespace': publish['namespace'],
    'slug': publish['slug'],
    'version': publish['version'],
    'publishStatus': publish['status'],
    'recommendationStatus': recommend['status'],
    'cacheStatus': recommend['cacheStatus'],
    'title': recommend.get('title'),
}, ensure_ascii=False, indent=2))
PY
