---
title: 配置说明
sidebar_position: 3
description: SkillHub 配置项详细说明
---

# 配置说明

## 环境变量

SkillHub 通过环境变量进行配置，主要配置项如下：

### 模板边界

- `.env.quickstart.example`：本地 Quickstart，使用 `http://localhost`、本地存储和
  `SESSION_COOKIE_SECURE=false`，不能用于生产。
- `.env.release.example`：生产/发布配置，使用 HTTPS、S3/OSS 和
  `SESSION_COOKIE_SECURE=true`。生产发布链路只读取该类配置，不读取 Quickstart 模板。
- `make validate-release-config` 默认校验 `.env.release`；校验 Quickstart 时显式使用
  `make validate-release-config RELEASE_ENV_FILE=.env.quickstart`。

### 基础配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SKILLHUB_PUBLIC_BASE_URL` | 公网访问地址 | - |
| `SKILLHUB_VERSION` | 镜像版本 | `edge` |

### 数据库配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `POSTGRES_HOST` | PostgreSQL 主机 | `postgres` |
| `POSTGRES_PORT` | PostgreSQL 端口 | `5432` |
| `POSTGRES_DB` | 数据库名 | `skillhub` |
| `POSTGRES_USER` | 数据库用户 | `skillhub` |
| `POSTGRES_PASSWORD` | 数据库密码 | - |

### Redis 配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `REDIS_HOST` | Redis 主机 | `redis` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | - |

### 存储配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `SKILLHUB_STORAGE_PROVIDER` | 存储提供方 | `local` |
| `SKILLHUB_STORAGE_S3_ENDPOINT` | S3 端点 | - |
| `SKILLHUB_STORAGE_S3_BUCKET` | S3 桶名 | - |
| `SKILLHUB_STORAGE_S3_ACCESS_KEY` | S3 Access Key | - |
| `SKILLHUB_STORAGE_S3_SECRET_KEY` | S3 Secret Key | - |

### OAuth 配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `OAUTH2_GITHUB_CLIENT_ID` | GitHub OAuth Client ID | - |
| `OAUTH2_GITHUB_CLIENT_SECRET` | GitHub OAuth Client Secret | - |

### 首登管理员配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `BOOTSTRAP_ADMIN_ENABLED` | 是否启用首登管理员 | `true` |
| `BOOTSTRAP_ADMIN_USERNAME` | 首登管理员用户名（启用时必填） | - |
| `BOOTSTRAP_ADMIN_PASSWORD` | 首登管理员强密码（启用时必填） | - |

本地源码开发使用 `local` profile 时，默认账号仍为 `admin` / `ChangeMe!2026`。该默认值仅供本地开发使用，不能用于 `docker` profile 或生产部署；生产首次部署前必须显式设置强密码。

## 配置文件

Spring Boot 配置文件位于 `server/skillhub-app/src/main/resources/`。

## 下一步

- [认证配置](../security/authentication) - 配置身份认证
