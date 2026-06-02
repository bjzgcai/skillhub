#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:-skill-security-scanner:smoke}"
CONTAINER_NAME="${CONTAINER_NAME:-skill-security-scanner-smoke}"
PORT="${PORT:-8020}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

docker build -t "$IMAGE_TAG" "$ROOT_DIR"
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER_NAME" -p "${PORT}:8020" "$IMAGE_TAG" >/dev/null

for _ in $(seq 1 40); do
  if curl -fsS "http://127.0.0.1:${PORT}/health" >/dev/null; then
    break
  fi
  sleep 0.5
done
curl -fsS "http://127.0.0.1:${PORT}/health" >/dev/null

python3 - "$TMP_DIR/bundle.zip" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "w") as archive:
    archive.writestr("SKILL.md", "---\nname: smoke-safe\nversion: 1.0.0\n---\nSmoke test skill.\n")
    archive.writestr("main.py", "print(\"hello\")\n")
PY

RESPONSE_FILE="$TMP_DIR/response.json"
curl -fsS -X POST "http://127.0.0.1:${PORT}/v1/scans:sync" \
  -F "file=@${TMP_DIR}/bundle.zip;type=application/zip" \
  -F "namespace=global" \
  -F "slug=smoke-safe" \
  -F "version=1.0.0" > "$RESPONSE_FILE"

python3 - "$RESPONSE_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as fh:
    payload = json.load(fh)

statuses = {item["name"]: item["status"] for item in payload["scanners"]}
missing = [name for name in ("skill-vetter", "semgrep", "osv-scanner") if statuses.get(name) != "completed"]
if missing:
    raise SystemExit(f"scanner(s) did not complete: {missing}; statuses={statuses}; payload={payload}")
if payload["verdict"] not in {"PASS", "WARN"}:
    raise SystemExit(f"unexpected verdict: {payload['verdict']}; payload={payload}")
print("container smoke passed", statuses, payload["verdict"])
PY
