#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROD_HOST="ubuntu@10.1.132.59"
REMOTE_OPS="/opt/skillhub/ops"
COMPONENT="all"
TAG=""
APPLY=0
SKIP_BUILD=0
SKIP_TRANSFER=0
SKIP_VERIFY=0

usage() {
  cat <<'USAGE'
Usage: ops/release-to-prod.sh [options]

Build SkillHub Docker images locally, transfer them to the production host,
run the production release plan, and optionally apply + verify it.

Options:
  --component <all|server|web>  Component to release. Default: all
  --tag <tag>                  Docker tag for both server/web images.
                               Default: prod-local-<utc>-<git-sha>
  --host <ssh-target>          Production SSH target. Default: ubuntu@10.1.132.59
  --apply                      Apply the release. Without this, only plan is executed.
  --skip-build                 Reuse local images with the selected tag.
  --skip-transfer              Assume images already exist on the production host.
  --skip-verify                Skip explicit post-apply verify-release.sh call.
  -h, --help                   Show this help.

Examples:
  ops/release-to-prod.sh
  ops/release-to-prod.sh --component all --apply
  ops/release-to-prod.sh --component web --tag prod-local-20260623T020000Z-weekly --apply
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --component) COMPONENT="${2:-}"; shift 2 ;;
    --tag) TAG="${2:-}"; shift 2 ;;
    --host) PROD_HOST="${2:-}"; shift 2 ;;
    --apply) APPLY=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --skip-transfer) SKIP_TRANSFER=1; shift ;;
    --skip-verify) SKIP_VERIFY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown arg: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$COMPONENT" in
  all|server|web) ;;
  *) echo "invalid --component: $COMPONENT" >&2; exit 2 ;;
esac

if [ -z "$PROD_HOST" ]; then
  echo '--host must not be empty' >&2
  exit 2
fi

cd "$REPO_ROOT"

GIT_SHA="$(git rev-parse --short HEAD)"
if [ -z "$TAG" ]; then
  TAG="prod-local-$(date -u +%Y%m%dT%H%M%SZ)-${GIT_SHA}"
fi

SERVER_IMAGE="skillhub-server:${TAG}"
WEB_IMAGE="skillhub-web:${TAG}"

require_clean_tree_for_apply() {
  if [ "$APPLY" -eq 1 ] && [ -n "$(git status --porcelain)" ]; then
    echo 'refusing to apply release with uncommitted changes; commit or stash first' >&2
    git status --short >&2
    exit 3
  fi
}

build_images() {
  if [ "$SKIP_BUILD" -eq 1 ]; then
    echo 'skip build: using existing local images'
    return
  fi

  case "$COMPONENT" in
    all|server)
      echo "building ${SERVER_IMAGE}"
      docker build -t "$SERVER_IMAGE" -f server/Dockerfile server
      ;;
  esac

  case "$COMPONENT" in
    all|web)
      echo "building ${WEB_IMAGE}"
      docker build -t "$WEB_IMAGE" -f web/Dockerfile web
      ;;
  esac
}

transfer_images() {
  if [ "$SKIP_TRANSFER" -eq 1 ]; then
    echo 'skip transfer: assuming images exist on production host'
    return
  fi

  case "$COMPONENT" in
    all|server)
      echo "transferring ${SERVER_IMAGE} to ${PROD_HOST}"
      docker save "$SERVER_IMAGE" | ssh "$PROD_HOST" 'docker load'
      ;;
  esac

  case "$COMPONENT" in
    all|web)
      echo "transferring ${WEB_IMAGE} to ${PROD_HOST}"
      docker save "$WEB_IMAGE" | ssh "$PROD_HOST" 'docker load'
      ;;
  esac
}

remote_deploy_args() {
  printf '%q ' "$REMOTE_OPS/deploy-release.sh" --component "$COMPONENT"
  case "$COMPONENT" in
    all|server) printf '%q ' --server-tag "$TAG" ;;
  esac
  case "$COMPONENT" in
    all|web) printf '%q ' --web-tag "$TAG" ;;
  esac
  if [ "$APPLY" -eq 1 ]; then
    printf '%q ' --apply
  fi
}

run_plan_or_apply() {
  local cmd
  cmd="$(remote_deploy_args)"
  echo "remote deploy command: ${cmd}"
  ssh "$PROD_HOST" "$cmd"
}

verify_remote() {
  if [ "$APPLY" -ne 1 ] || [ "$SKIP_VERIFY" -eq 1 ]; then
    return
  fi

  echo "running remote status and verify for ${COMPONENT}"
  ssh "$PROD_HOST" "$REMOTE_OPS/status.sh ${COMPONENT}"
  ssh "$PROD_HOST" "$REMOTE_OPS/verify-release.sh ${COMPONENT}"
}

cat <<INFO
release target
  repo:      ${REPO_ROOT}
  commit:    ${GIT_SHA}
  host:      ${PROD_HOST}
  component: ${COMPONENT}
  tag:       ${TAG}
  apply:     ${APPLY}
INFO

require_clean_tree_for_apply
build_images
transfer_images
run_plan_or_apply
verify_remote

if [ "$APPLY" -eq 0 ]; then
  cat <<INFO

Dry-run complete. No production containers were changed.
To apply this exact tag, run:
  ops/release-to-prod.sh --component ${COMPONENT} --tag ${TAG} --apply
INFO
else
  echo "production release completed for ${COMPONENT} with tag ${TAG}"
fi
