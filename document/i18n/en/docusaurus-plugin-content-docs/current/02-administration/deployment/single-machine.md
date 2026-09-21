---
title: Single Machine Deployment
sidebar_position: 1
description: Deploy SkillHub using Docker Compose on a single machine
---

# Single Machine Deployment

This guide describes how to deploy SkillHub on a single server using Docker Compose.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose Plugin 2.0+
- At least 4GB available RAM
- At least 20GB available disk space

## Production Deployment

```bash
# 1. Clone the repository
git clone https://github.com/iflytek/skillhub.git
cd skillhub

# 2. Copy production environment template
cp .env.release.example .env.release

# 3. Edit configuration
# Modify configuration items in .env.release, especially strong passwords and public URLs
# Set BOOTSTRAP_ADMIN_PASSWORD explicitly before the first deployment; production has no default admin password

# 4. Validate production configuration
make validate-release-config

# 5. Start services
docker compose --env-file .env.release -f compose.release.yml up -d
```

## Configuration

See [Configuration](./configuration) documentation for details.

## Verify Deployment

```bash
# Check container status
docker compose --env-file .env.release -f compose.release.yml ps

# Check backend health
curl -i http://127.0.0.1:8080/actuator/health

# Access Web UI
# Open the configured HTTPS public URL in a browser
```

## First Login Configuration

1. If bootstrap admin is enabled, log in with the `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` explicitly set in `.env.release` before deployment; production has no default admin password
2. Change admin password immediately
3. Configure enterprise SSO (optional)
4. Create team namespaces

## Next Steps

- [Configuration](./configuration) - Detailed configuration reference
- [Kubernetes Deployment](./kubernetes) - High availability deployment
