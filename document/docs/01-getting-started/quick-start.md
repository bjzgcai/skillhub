---
title: 快速开始
sidebar_position: 2
description: 一键启动 SkillHub 开发环境
---

# 快速开始

## 一键启动

使用以下命令一键启动完整的 SkillHub 环境：

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

或者克隆仓库后手动启动：

```bash
git clone https://github.com/iflytek/skillhub.git
cd skillhub
make dev-all
```

## 默认账号

源码本地开发（`make dev-all`）会创建一个仅用于本地的 bootstrap 管理员：

- 用户名：`admin`
- 密码：`ChangeMe!2026`

### `curl` 一键部署

| 服务 | 地址 |
|------|------|
| Web UI | http://localhost |
| Backend API | http://localhost:8080 |

runtime 部署是本地 Quickstart 入口，没有默认管理员密码。首次运行会在 runtime
目录生成 `.env.quickstart`，请设置 `BOOTSTRAP_ADMIN_PASSWORD` 后重新执行启动命令，
再使用你设置的账号密码登录。Quickstart 使用本地存储、关闭企业 SSO，并通过
`http://localhost` 访问，因此 `SESSION_COOKIE_SECURE=false` 是有意的本地配置。
生产环境不能使用 `.env.quickstart`，必须使用 HTTPS 地址和
`SESSION_COOKIE_SECURE=true` 的 `.env.release`。

### `make dev-all` 本地开发

| 服务 | 地址 |
|------|------|
| Web UI | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |

除了上述 bootstrap 管理员，本地开发还预置两个模拟用户（无需密码）：

| 用户 | 角色 | 说明 |
|------|------|------|
| `local-user` | 普通用户 | 可发布技能、管理命名空间 |
| `local-admin` | 超级管理员 | 拥有所有权限，包括审核和用户管理 |

使用 `X-Mock-User-Id` 请求头切换模拟用户。
如需关闭 bootstrap 管理员，启动前设置 `BOOTSTRAP_ADMIN_ENABLED=false`。

## 常用命令

```bash
# 启动完整开发环境
make dev-all

# 停止所有服务
make dev-all-down

# 重置并重新启动
make dev-all-reset

# 仅启动后端
make dev

# 仅启动前端
make dev-web

# 查看所有可用命令
make help
```

## 下一步

- [产品概述](./overview) - 深入了解产品特性
- [典型应用场景](./use-cases) - 探索企业应用场景
- [单机部署](../administration/deployment/single-machine) - 生产环境部署指南
