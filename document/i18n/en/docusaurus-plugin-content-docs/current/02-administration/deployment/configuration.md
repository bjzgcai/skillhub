---
title: Configuration Reference
sidebar_position: 3
description: Detailed SkillHub configuration reference
---

# Configuration Reference

## Environment Variables

SkillHub is configured through environment variables. The main configuration items are listed below:

### Template Boundaries

- `.env.quickstart.example`: local Quickstart using `http://localhost`, local
  storage, and `SESSION_COOKIE_SECURE=false`; it must not be used in production.
- `.env.release.example`: production/release configuration using HTTPS,
  S3/OSS, and `SESSION_COOKIE_SECURE=true`. The production release path reads
  this configuration class and never the Quickstart template.
- `make validate-release-config` validates `.env.release` by default. To
  validate Quickstart explicitly, use
  `make validate-release-config RELEASE_ENV_FILE=.env.quickstart`.

### Basic Configuration

| Environment Variable | Description | Default Value |
|---------------------|-------------|---------------|
| `SKILLHUB_PUBLIC_BASE_URL` | Public access URL | - |
| `SKILLHUB_VERSION` | Image version | `edge` |

### Database Configuration

| Environment Variable | Description | Default Value |
|---------------------|-------------|---------------|
| `POSTGRES_HOST` | PostgreSQL host | `postgres` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | Database name | `skillhub` |
| `POSTGRES_USER` | Database user | `skillhub` |
| `POSTGRES_PASSWORD` | Database password | - |

### Redis Configuration

| Environment Variable | Description | Default Value |
|---------------------|-------------|---------------|
| `REDIS_HOST` | Redis host | `redis` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | - |

### Storage Configuration

| Environment Variable | Description | Default Value |
|---------------------|-------------|---------------|
| `SKILLHUB_STORAGE_PROVIDER` | Storage provider | `local` |
| `SKILLHUB_STORAGE_S3_ENDPOINT` | S3 endpoint | - |
| `SKILLHUB_STORAGE_S3_BUCKET` | S3 bucket name | - |
| `SKILLHUB_STORAGE_S3_ACCESS_KEY` | S3 Access Key | - |
| `SKILLHUB_STORAGE_S3_SECRET_KEY` | S3 Secret Key | - |

### OAuth Configuration

| Environment Variable | Description | Default Value |
|---------------------|-------------|---------------|
| `OAUTH2_GITHUB_CLIENT_ID` | GitHub OAuth Client ID | - |
| `OAUTH2_GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | - |

### Bootstrap Admin Configuration

| Environment Variable | Description | Default Value |
|---------------------|-------------|---------------|
| `BOOTSTRAP_ADMIN_ENABLED` | Enable bootstrap admin | `true` |
| `BOOTSTRAP_ADMIN_USERNAME` | Bootstrap admin username (required when enabled) | - |
| `BOOTSTRAP_ADMIN_PASSWORD` | Strong bootstrap admin password (required when enabled) | - |

For local source development with the `local` profile, the default account remains `admin` / `ChangeMe!2026`. This default is for local development only and must not be used with the `docker` profile or in production; production deployments must set a strong password explicitly before first startup.

## Configuration Files

Spring Boot configuration files are located at `server/skillhub-app/src/main/resources/`.

## Next Steps

- [Authentication Configuration](../security/authentication) - Configure authentication
