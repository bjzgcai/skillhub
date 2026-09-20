# skillhub 部署架构与运维

## 1 运行模型

仓库中有两种面向使用者的 Compose 运行方式，另有一条当前生产发布链路：

| 场景 | 入口 | 应用运行位置 | Compose 文件职责 |
|------|------|--------------|------------------|
| 本地源码开发 | `make dev-all` | server/web 在宿主机 | `docker-compose.yml` 只启动 PostgreSQL、Redis、MinIO 和源码 scanner |
| Quickstart / 单机体验 | `docker compose --env-file .env.quickstart -f compose.release.yml up -d` | server/web 在容器内 | `compose.release.yml` 启动发布镜像、PostgreSQL、Redis 和可选 scanner；默认 HTTP、本地存储 |
| 生产单机发布 | `docker compose --env-file .env.release -f compose.release.yml up -d`（仅适用于 Compose 生产部署） | server/web 在容器内 | 使用 HTTPS、S3/OSS、强密码和 `SESSION_COOKIE_SECURE=true` |
| 当前生产发布 | `ops/release-to-prod.sh` → `ops/deploy-release.sh --apply` | server/web 由生产机 `docker run` 管理 | release Compose 只作为配置快照和交付参考，不是当前生产切换命令 |

开发和交付使用 GitHub Actions 发布的多架构镜像，默认覆盖 `linux/amd64` 与 `linux/arm64`。

`docker-compose.yml` 与 `compose.release.yml` 不能互换：前者允许本地开发默认值并将应用跑在宿主机，后者要求发布环境变量并将完整应用跑在容器内。staging 使用 `docker-compose.yml` 加 `docker-compose.staging.yml` 覆盖层，属于开发 Compose 的复用场景。

不再维护本地构建整套 demo 容器的中间模式，也不再保留 `docker-compose.prod.yml`。

## 2 单机交付拓扑

```
┌──────────────┐
│ Browser / CLI│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Web/Nginx  │  published image
└──────┬───────┘
       │ /api/*
       ▼
┌──────────────┐
│ Spring Boot  │  published image
└───┬────┬─────┘
    │    │
    ▼    ▼
 PostgreSQL  Redis
```

说明：
- Web 容器提供静态资源，并将 `/api/*`、`/oauth2/*`、`/.well-known/*` 反代到后端
- 后端默认运行 `docker` profile，不再启用本地 mock 登录
- PostgreSQL / Redis 默认只绑定 `127.0.0.1`
- 对象存储推荐使用外部 S3 / OSS，通过环境变量注入

## 3 Profile 约定

| Profile | 用途 | 说明 |
|---------|------|------|
| `local` | 本地源码开发能力 | 启用 mock 登录、开发种子账号、调试日志 |
| `docker` | 容器运行时能力 | 启用容器运行时相关能力，不会自动打开首登管理员 |

单机交付环境使用 `SPRING_PROFILES_ACTIVE=docker`，原因如下：

- 生产环境不应开启 `X-Mock-User-Id` 这一类本地开发旁路能力
- 容器环境仍然保留 `docker` profile 的运行时能力，首个管理员账户初始化不依赖该 profile，通过环境变量控制
- 数据库、Redis、OSS、站点公网地址全部改为环境变量优先

如需启用首登管理员，来源于以下环境变量：

- `BOOTSTRAP_ADMIN_ENABLED=true`（两个模板默认开启；生产可显式关闭）
- `BOOTSTRAP_ADMIN_USERNAME`（启用首登管理员时显式设置）
- `BOOTSTRAP_ADMIN_PASSWORD`（启用首登管理员时显式设置强密码）

本地源码开发使用 `local` profile 时，仍可使用默认账号 `admin` / `ChangeMe!2026`；该默认值仅限本地开发，不能用于 `docker` profile 或任何生产部署。

生产部署要求：

- 首次部署前必须显式设置唯一且足够强的 `BOOTSTRAP_ADMIN_PASSWORD`；`validate-release-config.sh` 会拒绝本地默认值和其它示例密码
- 如启用首登管理员，同时显式设置 `BOOTSTRAP_ADMIN_USERNAME`
- 完成首次登录后立即修改管理员密码
- 如果已有外部身份源，通常不需要启用 bootstrap admin
- `SKILLHUB_PUBLIC_BASE_URL` 应配置为最终 HTTPS 域名，避免 OAuth / Cookie / 设备码链接异常

## 4 开发环境

开发入口保持不变：

```bash
make dev-all
```

行为：

- `docker-compose.yml` 启动 PostgreSQL、Redis、MinIO 和源码 scanner
- `server` 在宿主机通过 Maven Wrapper 启动
- `web` 在宿主机通过 Vite 启动

常用命令：

```bash
make dev
make dev-all
make dev-down
make dev-all-down
make dev-all-reset
```

## 5 Quickstart 与生产单机环境

### 5.1 Quickstart 启动

```bash
cp .env.quickstart.example .env.quickstart
# 先在 .env.quickstart 中设置 BOOTSTRAP_ADMIN_PASSWORD 和 POSTGRES_PASSWORD
make validate-release-config RELEASE_ENV_FILE=.env.quickstart
docker compose --env-file .env.quickstart -f compose.release.yml up -d
```

Quickstart 使用 `http://localhost`、本地对象存储和
`SESSION_COOKIE_SECURE=false`，仅用于本地体验，不能直接用于生产。

### 5.2 生产单机启动

```bash
cp .env.release.example .env.release
# 填写最终 HTTPS 地址、S3/OSS 凭据、数据库密码和首登管理员密码
make validate-release-config RELEASE_ENV_FILE=.env.release
docker compose --env-file .env.release -f compose.release.yml up -d
```

生产必须使用 HTTPS、`SESSION_COOKIE_SECURE=true`、外部 S3/OSS，并保持
`SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED=false`。

`make validate-release-config` 会根据文件名选择校验模式：`.env.quickstart`
按本地 HTTP/本地存储规则校验，`.env.release` 按生产 HTTPS/S3 规则校验。
如果直接使用 Compose 启用安全扫描，需要显式带上对应 profile，例如：

```bash
COMPOSE_PROFILES=secret-scan,unified-scan \
  docker compose --env-file .env.release -f compose.release.yml up -d
```

默认访问地址：

- Web UI: `SKILLHUB_PUBLIC_BASE_URL`
- Backend API: `http://localhost:8080`

### 5.3 关键文件

- `compose.release.yml`
  - 使用发布镜像，不在用户机器上执行本地构建
  - 负责拉起 PostgreSQL、Redis、server、web
  - PostgreSQL、Redis 和 server API 默认只绑定到 `127.0.0.1`
  - Web 和后端都支持运行时环境变量注入，不需要为每个环境重建镜像
- `.env.quickstart.example`
  - 本地 HTTP Quickstart 变量模板
- `.env.release.example`
  - HTTPS 生产/发布变量模板
  - 包含镜像名、镜像版本、端口、数据库凭证、外部 OSS、站点公网地址和首登管理员参数
- `scripts/validate-release-config.sh`
  - 在启动前校验显式传入的环境变量文件
  - 可提前拦截占位值、URL 格式错误、缺失的 OSS 凭据、危险的明文默认值

### 5.4 镜像标签约定

- `edge`
  - `main` 分支最新构建
  - 用于内部持续验证
- `vX.Y.Z`
  - 对应 Git tag
  - 用于稳定版本交付
- `latest`
  - 仅在语义化版本 tag 发布时更新

推荐：

- 默认快速启动：`SKILLHUB_VERSION=latest`
- 团队内部试用：`SKILLHUB_VERSION=edge`
- 对外演示或严格可复现环境：固定为某个 `vX.Y.Z`

### 5.5 与当前生产发布的关系

`compose.release.yml` 是面向用户的单机 Quickstart 入口，不是当前生产机的唯一或直接控制入口。当前生产发布由以下链路负责：

```text
repo/ops/release-to-prod.sh
        ↓ SSH
/opt/skillhub/ops/deploy-release.sh
        ↓
/opt/skillhub/ops/release-lib.sh
        ↓
docker run skillhub-server-1 / skillhub-web-1
```

生产脚本会根据 `ops/templates/compose.release.yml.tpl` 生成 release 配置快照，用于记录本次发布的环境和镜像信息；远端 `deploy-release.sh` 在 plan/apply 前会校验合并后的 `env.release` 与 `secrets.env`，`--apply` 阶段实际通过 `release-lib.sh` 创建或替换应用容器，并执行健康检查、配置检查和失败回滚。

因此：

- 修改 `compose.release.yml` 会影响 Quickstart，不会自动改变当前生产发布行为；
- 修改 `ops/templates/compose.release.yml.tpl` 会影响生产 release 快照和配置记录；
- 修改 `ops/release-lib.sh` 才会改变当前生产 `docker run` 的容器启动参数；
- 三处关键安全配置必须保持一致，并由 `make test-ops` 的配置契约测试校验。

## 6 GitHub Actions 发布流程

发布工作流文件：`.github/workflows/publish-images.yml`

触发条件：

- `release.published`
- 手动 `workflow_dispatch`

流程：

1. 检出代码
2. 登录 GHCR
3. 分别构建 `server/Dockerfile` 与 `web/Dockerfile`
4. 推送镜像：
   - `ghcr.io/iflytek/skillhub-server`
   - `ghcr.io/iflytek/skillhub-web`
5. 写入 `edge` / `vX.Y.Z` / `latest` / `sha-*` 标签
6. 同时发布 `linux/amd64` 与 `linux/arm64` manifest，避免 Apple Silicon / ARM 主机依赖模拟层

## 7 配置管理

前端运行时配置通过 `web/runtime-config.js.template` 注入。与认证兼容层相关的新变量如下：

- `SKILLHUB_WEB_AUTH_DIRECT_ENABLED`
  - 是否在前端打开账号密码兼容接入层
  - 默认应为 `false`
- `SKILLHUB_WEB_AUTH_DIRECT_PROVIDER`
  - 前端调用 `/api/v1/auth/direct/login` 时使用的 provider，例如 `private-sso`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED`
  - 是否在前端打开企业 SSO 被动会话兼容入口
  - 默认应为 `false`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_PROVIDER`
  - 前端调用 `/api/v1/auth/session/bootstrap` 时使用的 provider，例如 `private-sso`
- `SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_AUTO`
  - 是否在登录页加载后自动尝试一次 bootstrap
  - 建议私有版初期保持 `false`

注意：

- 前端密码兼容层打开之前，后端仍必须同步打开 `skillhub.auth.direct.enabled=true`
- 前端开关打开之前，后端仍必须同步打开 `skillhub.auth.session-bootstrap.enabled=true`
- 前后端任一侧未开启，都不会破坏原有登录方式；只会使该兼容入口不可用或不显示

开发环境：

- 本地命令与 `docker-compose.yml`
- 非敏感默认值可直接落库或写入本地配置

Quickstart 环境：

- 使用 `.env.quickstart` 管理本地 Compose 变量
- 默认访问 `http://localhost`
- `SESSION_COOKIE_SECURE=false` 只适用于本地 HTTP Quickstart

生产单机环境：

- 使用 `.env.release` 管理 Compose 变量
- `SKILLHUB_PUBLIC_BASE_URL` 必须是最终 HTTPS 地址
- `SESSION_COOKIE_SECURE=true`，不得复用 `.env.quickstart`
- 如果 GHCR 包保持私有，用户需要先 `docker login ghcr.io`
- 推荐将敏感变量放入 CI/CD Secret 或主机上的受控 `.env.release`
- 外部对象存储通过 `SKILLHUB_STORAGE_S3_*` 注入
- 前端反代和运行时 API 地址通过 `SKILLHUB_API_UPSTREAM` / `SKILLHUB_WEB_API_BASE_URL` 注入
- 如果要开放真实登录，再补充 `OAUTH2_GITHUB_CLIENT_ID` / `OAUTH2_GITHUB_CLIENT_SECRET`

## 8 裸金属上线清单

推荐顺序：

1. 准备服务器基础环境
   - 安装 Docker Engine 与 Docker Compose Plugin
   - 配置公网 HTTPS 入口，确保最终访问域名已经确定
   - 打开 `80` / `443`，避免直接暴露 `5432` / `6379`
2. 填写 `.env.release`（生产 HTTPS 配置，不要使用 `.env.quickstart`）
   - `SKILLHUB_PUBLIC_BASE_URL` 填最终 HTTPS 域名，且不要带尾部 `/`
   - `SKILLHUB_STORAGE_PROVIDER=s3`
   - 按云厂商 OSS / S3 兼容参数填写 `SKILLHUB_STORAGE_S3_*`
   - 设置非默认的 `POSTGRES_PASSWORD`
   - 如启用首登管理员，务必在首次启动前显式设置 `BOOTSTRAP_ADMIN_USERNAME` 和强密码 `BOOTSTRAP_ADMIN_PASSWORD`
3. 启动前校验
   - 运行 `make validate-release-config RELEASE_ENV_FILE=.env.release`
   - 确认没有 `replace-me`、`change-this-*`、`ChangeMe!2026` 之类的占位值
4. 首次启动
   - 运行 `docker compose --env-file .env.release -f compose.release.yml up -d`
   - 检查 `docker compose --env-file .env.release -f compose.release.yml ps`
   - 检查 `curl -i http://127.0.0.1:8080/actuator/health`
5. 首登收尾
   - 仅在启用了 `BOOTSTRAP_ADMIN_ENABLED=true` 时，使用 `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` 登录
   - 立即修改管理员密码
   - 如果后续完全走 OAuth，可将 `BOOTSTRAP_ADMIN_ENABLED=false`

## 9 可观测性

| 维度 | 方案 |
|------|------|
| 健康检查 | `web/nginx-health`、`server/actuator/health` |
| 日志 | 容器 stdout / stderr |
| 指标 | Spring Boot Actuator，后续可接 Prometheus |

## 10 安全扫描服务

如果要启用 `skill-scanner` 后端链路，当前仓库建议按下面的方式部署：

- 本地共享目录场景可以使用 `local` 模式
- Kubernetes 或分离部署场景应使用 `upload` 模式

当前 `deploy/k8s` 已按分离部署建模，因此推荐：

- `SKILLHUB_SECURITY_SCANNER_ENABLED=true`
- `SKILLHUB_SECURITY_SCANNER_URL=http://skillhub-scanner:8000`
- `SKILLHUB_SECURITY_SCANNER_MODE=upload`

相关文件：

- `deploy/k8s/scanner-deployment.yaml`
- `deploy/k8s/services.yaml`
- `deploy/k8s/backend-deployment.yaml`
- `scripts/verify-scanner.sh`
- `docs/security-scanning.md`

## 11 数据迁移

Flyway 仍是唯一 schema 变更入口：

- 路径：`server/skillhub-app/src/main/resources/db/migration/`
- 命名：`V{version}__{description}.sql`
- 启动策略：应用容器启动时自动执行迁移
