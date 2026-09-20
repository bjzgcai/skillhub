#!/bin/sh

set -eu

COMMAND="up"
if [ "$#" -gt 0 ] && [ "${1#-}" = "$1" ]; then
  COMMAND="$1"
  shift
fi

SKILLHUB_REF="${SKILLHUB_REF:-main}"
SKILLHUB_HOME_DEFAULT="${TMPDIR:-/tmp}/skillhub-runtime"
SKILLHUB_HOME="${SKILLHUB_HOME:-$SKILLHUB_HOME_DEFAULT}"
SKILLHUB_VERSION_VALUE="${SKILLHUB_VERSION:-}"
SKILLHUB_ALIYUN_REGISTRY="${SKILLHUB_ALIYUN_REGISTRY:-}"
SKILLHUB_ALIYUN_NAMESPACE="${SKILLHUB_ALIYUN_NAMESPACE:-skill_hub}"
SKILLHUB_MIRROR_REGISTRY_VALUE="${SKILLHUB_MIRROR_REGISTRY:-}"
SKILLHUB_SERVER_IMAGE_VALUE="${SKILLHUB_SERVER_IMAGE:-}"
SKILLHUB_WEB_IMAGE_VALUE="${SKILLHUB_WEB_IMAGE:-}"
POSTGRES_IMAGE_VALUE="${POSTGRES_IMAGE:-}"
REDIS_IMAGE_VALUE="${REDIS_IMAGE:-}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || { echo "Missing value for --version" >&2; exit 1; }
      SKILLHUB_VERSION_VALUE="$2"
      shift 2
      ;;
    --aliyun)
      if [ -z "$SKILLHUB_ALIYUN_REGISTRY" ] || [ -z "$SKILLHUB_ALIYUN_NAMESPACE" ]; then
        echo "SKILLHUB_ALIYUN_REGISTRY and SKILLHUB_ALIYUN_NAMESPACE must be configured for --aliyun" >&2
        exit 1
      fi
      SKILLHUB_MIRROR_REGISTRY_VALUE="${SKILLHUB_ALIYUN_REGISTRY%/}/${SKILLHUB_ALIYUN_NAMESPACE}"
      shift
      ;;
    --mirror-registry)
      [ "$#" -ge 2 ] || { echo "Missing value for --mirror-registry" >&2; exit 1; }
      SKILLHUB_MIRROR_REGISTRY_VALUE="$2"
      shift 2
      ;;
    --home)
      [ "$#" -ge 2 ] || { echo "Missing value for --home" >&2; exit 1; }
      SKILLHUB_HOME="$2"
      shift 2
      ;;
    --ref)
      [ "$#" -ge 2 ] || { echo "Missing value for --ref" >&2; exit 1; }
      SKILLHUB_REF="$2"
      shift 2
      ;;
    --server-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --server-image" >&2; exit 1; }
      SKILLHUB_SERVER_IMAGE_VALUE="$2"
      shift 2
      ;;
    --web-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --web-image" >&2; exit 1; }
      SKILLHUB_WEB_IMAGE_VALUE="$2"
      shift 2
      ;;
    --postgres-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --postgres-image" >&2; exit 1; }
      POSTGRES_IMAGE_VALUE="$2"
      shift 2
      ;;
    --redis-image)
      [ "$#" -ge 2 ] || { echo "Missing value for --redis-image" >&2; exit 1; }
      REDIS_IMAGE_VALUE="$2"
      shift 2
      ;;
    --help|-h)
      cat <<EOF
Usage: sh runtime.sh [up|down|clean|ps|logs|pull] [options]

Options:
  --version <tag>       Use a specific image tag, for example v0.1.0
  --aliyun              Use the configured Aliyun mirror registry
  --mirror-registry <r> Use mirrored images from <registry>/<namespace>
  --home <dir>          Store runtime files in a specific directory
  --ref <git-ref>       Download runtime files from a specific Git ref
  --server-image <img>  Override backend image repository
  --web-image <img>     Override frontend image repository
  --postgres-image <i>  Override PostgreSQL image
  --redis-image <img>   Override Redis image
EOF
      exit 0
      ;;
    *)
      echo "Unsupported argument: $1" >&2
      exit 1
      ;;
  esac
done

SKILLHUB_RAW_BASE="${SKILLHUB_RAW_BASE:-https://raw.githubusercontent.com/iflytek/skillhub/$SKILLHUB_REF}"
COMPOSE_FILE="$SKILLHUB_HOME/compose.release.yml"
ENV_EXAMPLE_FILE="$SKILLHUB_HOME/.env.quickstart.example"
ENV_FILE="$SKILLHUB_HOME/.env.quickstart"
LEGACY_ENV_FILE="$SKILLHUB_HOME/.env.release"
VALIDATE_CONFIG_FILE="$SKILLHUB_HOME/validate-release-config.sh"
RUNTIME_VALIDATION_MODE="quickstart"

find_compose() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
    return 0
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    echo "docker-compose"
    return 0
  fi

  echo "Docker Compose is required." >&2
  exit 1
}

download_file() {
  src="$1"
  dest="$2"
  tmp="$dest.tmp"
  curl -fsSL "$src" -o "$tmp"
  mv "$tmp" "$dest"
}

set_env_value() {
  key="$1"
  value="$2"

  if [ ! -f "$ENV_FILE" ]; then
    return 0
  fi

  tmp="$ENV_FILE.tmp"
  if grep -q "^$key=" "$ENV_FILE"; then
    sed "s|^$key=.*|$key=$value|" "$ENV_FILE" >"$tmp"
  else
    cat "$ENV_FILE" >"$tmp"
    printf '%s=%s\n' "$key" "$value" >>"$tmp"
  fi
  mv "$tmp" "$ENV_FILE"
}

ensure_env_value() {
  key="$1"
  value="$2"

  if ! grep -q "^$key=" "$ENV_FILE"; then
    printf '%s=%s\n' "$key" "$value" >>"$ENV_FILE"
  fi
}

prepare_runtime_files() {
  mkdir -p "$SKILLHUB_HOME"
  download_file "$SKILLHUB_RAW_BASE/compose.release.yml" "$COMPOSE_FILE"
  download_file "$SKILLHUB_RAW_BASE/.env.quickstart.example" "$ENV_EXAMPLE_FILE"
  download_file "$SKILLHUB_RAW_BASE/scripts/validate-release-config.sh" "$VALIDATE_CONFIG_FILE"
  chmod 700 "$VALIDATE_CONFIG_FILE"

  # Keep existing runtime directories usable after the template split. New
  # installations always use the explicit Quickstart file.
  if [ ! -f "$ENV_FILE" ] && [ -f "$LEGACY_ENV_FILE" ]; then
    ENV_FILE="$LEGACY_ENV_FILE"
    RUNTIME_VALIDATION_MODE="production"
    ensure_env_value "SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED" "false"
    ensure_env_value "BOOTSTRAP_ADMIN_ENABLED" "false"
    echo "Using legacy runtime config: $ENV_FILE" >&2
  fi

  if [ ! -f "$ENV_FILE" ]; then
    cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
  fi

  if [ -n "$SKILLHUB_MIRROR_REGISTRY_VALUE" ]; then
    mirror_registry="${SKILLHUB_MIRROR_REGISTRY_VALUE%/}"
    if [ -z "$POSTGRES_IMAGE_VALUE" ]; then
      POSTGRES_IMAGE_VALUE="$mirror_registry/postgres:16-alpine"
    fi
    if [ -z "$REDIS_IMAGE_VALUE" ]; then
      REDIS_IMAGE_VALUE="$mirror_registry/redis:7-alpine"
    fi
    if [ -z "$SKILLHUB_SERVER_IMAGE_VALUE" ]; then
      SKILLHUB_SERVER_IMAGE_VALUE="$mirror_registry/skillhub-server"
    fi
    if [ -z "$SKILLHUB_WEB_IMAGE_VALUE" ]; then
      SKILLHUB_WEB_IMAGE_VALUE="$mirror_registry/skillhub-web"
    fi
  fi

  if [ -n "$SKILLHUB_VERSION_VALUE" ]; then
    set_env_value "SKILLHUB_VERSION" "$SKILLHUB_VERSION_VALUE"
  fi

  if [ -n "$POSTGRES_IMAGE_VALUE" ]; then
    set_env_value "POSTGRES_IMAGE" "$POSTGRES_IMAGE_VALUE"
  fi

  if [ -n "$REDIS_IMAGE_VALUE" ]; then
    set_env_value "REDIS_IMAGE" "$REDIS_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_SERVER_IMAGE_VALUE" ]; then
    set_env_value "SKILLHUB_SERVER_IMAGE" "$SKILLHUB_SERVER_IMAGE_VALUE"
  fi

  if [ -n "$SKILLHUB_WEB_IMAGE_VALUE" ]; then
    set_env_value "SKILLHUB_WEB_IMAGE" "$SKILLHUB_WEB_IMAGE_VALUE"
  fi
}

validate_runtime_config() {
  "$VALIDATE_CONFIG_FILE" "$ENV_FILE" "$RUNTIME_VALIDATION_MODE"
}

env_value() {
  key="$1"
  sed -n "s/^$key=//p" "$ENV_FILE" | tail -n 1
}

run_compose() {
  compose_cmd="$(find_compose)"
  profiles=""
  if [ "$(env_value SKILLHUB_SECRET_SCAN_ENABLED)" = "true" ]; then
    profiles="secret-scan"
  fi
  if [ "$(env_value SKILLHUB_SECURITY_UNIFIED_SCAN_ENABLED)" = "true" ]; then
    if [ -n "$profiles" ]; then
      profiles="$profiles,unified-scan"
    else
      profiles="unified-scan"
    fi
  fi
  # shellcheck disable=SC2086
  if [ -n "$profiles" ]; then
    COMPOSE_PROFILES="$profiles" $compose_cmd --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  else
    $compose_cmd --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
  fi
}

prepare_runtime_files

case "$COMMAND" in
  up)
    validate_runtime_config
    run_compose up -d
    if [ -f "$0" ] && [ "$0" != "sh" ] && [ "$0" != "/bin/sh" ] && [ "$0" != "/usr/bin/sh" ]; then
      stop_command="sh \"$0\" down --home \"$SKILLHUB_HOME\""
    else
      stop_command="re-run the verified runtime script with: sh <runtime-script> down --home \"$SKILLHUB_HOME\""
    fi
    cat <<EOF
SkillHub runtime started.
Web UI: http://localhost
Backend API: http://localhost:8080
Runtime dir: $SKILLHUB_HOME
Config file: $ENV_FILE
Stop with:
  $stop_command
EOF
    ;;
  down)
    run_compose down
    ;;
  clean)
    run_compose down
    rm -rf "$SKILLHUB_HOME"
    ;;
  ps)
    run_compose ps
    ;;
  logs)
    run_compose logs -f
    ;;
  pull)
    run_compose pull
    ;;
  *)
    echo "Unsupported command: $COMMAND" >&2
    echo "Usage: sh runtime.sh [up|down|clean|ps|logs|pull] [options]" >&2
    exit 1
    ;;
esac
