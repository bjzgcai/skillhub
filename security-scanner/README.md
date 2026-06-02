# Skill Security Scanner

Unified security scanning service for SkillHub skill bundles.

This service is the single scanner API that SkillHub calls. It is intended to orchestrate:

- `skill-vetter` for skill-specific policy and red-flag checks
- `semgrep` for static analysis
- `osv-scanner` for dependency vulnerability checks

The current implementation defines the API contract, validates uploaded bundles, runs the built-in `skill-vetter` rules, and calls `semgrep` / `osv-scanner` when those binaries are available in the runtime image.

## API

- `GET /health` - liveness/readiness check
- `POST /v1/scans:sync` - synchronous bundle scan for pre-publish gates

See `docs/openapi.yaml` for the contract.

## Local Run

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8020
```

## Docker

```bash
docker build -t skill-security-scanner:dev security-scanner
docker run --rm -p 8020:8020 skill-security-scanner:dev
curl http://127.0.0.1:8020/health
```

## Current Behavior

The current implementation validates the uploaded zip package, extracts it safely, runs built-in `skill-vetter` red-flag checks, and invokes external `semgrep` / `osv-scanner` binaries when present. Missing external binaries are reported as `skipped` so SkillHub can still consume a stable report shape during staged rollout.


## Adapter Behavior

- `skill-vetter` is built into this service and currently checks common high-risk skill patterns such as credential file access, private OpenClaw memory/persona file references, browser cookie storage references, `curl | sh`, sudo-like privilege escalation, dynamic execution, and base64 decode indicators.
- `semgrep` is invoked as `semgrep scan --json --config=auto --error <dir>` when the `semgrep` binary exists.
- `osv-scanner` is invoked as `osv-scanner --format json -r <dir>` when the `osv-scanner` binary exists.
- The policy engine returns `FAIL` for critical findings and blocking `skill-vetter` high findings, `MANUAL_REVIEW` for other high findings, `WARN` for medium/low findings, and `PASS` when no findings are present.
