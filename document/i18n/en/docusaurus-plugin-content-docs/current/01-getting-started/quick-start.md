---
title: Quick Start
sidebar_position: 2
description: One-click startup of SkillHub development environment
---

# Quick Start

## One-click Startup

Use the following command to start a complete SkillHub environment with one command:

```bash
RUNTIME_URL=https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh
RUNTIME_FILE=/tmp/skillhub-runtime.sh
SHA256SUM_URL="${SHA256SUM_URL:-}"
SHA256SUM="${SHA256SUM:-}"
curl -fL "$RUNTIME_URL" -o "$RUNTIME_FILE"
if [ -n "$SHA256SUM_URL" ]; then
  curl -fL "$SHA256SUM_URL" -o "${RUNTIME_FILE}.sha256"
  (cd "$(dirname "$RUNTIME_FILE")" && sha256sum -c "$(basename "${RUNTIME_FILE}.sha256")")
elif [ -n "$SHA256SUM" ]; then
  printf '%s  %s\n' "$SHA256SUM" "$(basename "$RUNTIME_FILE")" |
    (cd "$(dirname "$RUNTIME_FILE")" && sha256sum -c -)
else
  echo "Set SHA256SUM_URL or SHA256SUM from a trusted release source before executing." >&2
  exit 1
fi
sh "$RUNTIME_FILE" up
```

This moving quick-start URL does not include a checksum in the repository. Set
exactly one of `SHA256SUM_URL` (a trusted `sha256sum`-format file) or
`SHA256SUM` (the exact trusted SHA-256 value) before executing. If no trusted
checksum is available, stop after downloading and do not execute the script.

Or clone the repository and start manually:

```bash
git clone https://github.com/iflytek/skillhub.git
cd skillhub
make dev-all
```

## Default Account

Source-based local development (`make dev-all`) creates a bootstrap admin for
local use only:

- username: `admin`
- password: `ChangeMe!2026`

### `curl` One-click Deployment

| Service | Address |
|---------|---------|
| Web UI | http://localhost |
| Backend API | http://localhost:8080 |

The runtime deployment is the local Quickstart path and has no default admin
password. On the first run it creates `.env.quickstart` in the runtime
directory; set `BOOTSTRAP_ADMIN_PASSWORD` there, rerun the startup command,
and log in with the credentials you chose. Quickstart uses local storage,
disables enterprise SSO, and intentionally sets `SESSION_COOKIE_SECURE=false`
for `http://localhost`. Do not use `.env.quickstart` or this HTTP setting in
production; production must use `.env.release` with an HTTPS URL and
`SESSION_COOKIE_SECURE=true`.

### `make dev-all` Local Development

| Service | Address |
|---------|---------|
| Web UI | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |

In addition to the bootstrap admin, local development includes two mock users (no password needed):

| User | Role | Description |
|------|------|-------------|
| `local-user` | Regular user | Can publish skills, manage namespaces |
| `local-admin` | Super admin | Has all permissions including review and user management |

Use the `X-Mock-User-Id` request header to switch mock users.
To disable the bootstrap admin, set `BOOTSTRAP_ADMIN_ENABLED=false` before starting.

## Common Commands

```bash
# Start complete development environment
make dev-all

# Stop all services
make dev-all-down

# Reset and restart
make dev-all-reset

# Start backend only
make dev

# Start frontend only
make dev-web

# View all available commands
make help
```

## Next Steps

- [Overview](./overview) - Deep dive into product features
- [Use Cases](./use-cases) - Explore enterprise application scenarios
- [Single Machine Deployment](../administration/deployment/single-machine) - Production deployment guide
