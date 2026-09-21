# SkillHub Ops Scripts

这套目录是 SkillHub 运维脚本的 **Git 源码目录**。

## 设计原则

- Git 管理：`/home/ubuntu/bjzgcai/skillhub/ops`
- 运行副本：`/opt/skillhub/ops`
- release 模板运行副本：`/opt/skillhub/releases/templates`
- 共享配置与 secrets 仍留在 `/opt/skillhub/shared`，不进入 Git
- `releases/`、`current`、真实生产 env / secrets 不进入 Git

一句话：
**repo 里的是源码，/opt/skillhub 里的是运行态。**

## 主要脚本

- `release-to-prod.sh`：repo 侧一键发布编排，串联本地构建、镜像传输、生产 plan/apply 与验收
- `deploy-release.sh`：生产机标准发布入口，支持 `--component web|server|all` 与 `--apply`
- `release-lib.sh`：公共函数库，供 deploy / rollback / status 等复用
- `verify-server-release.sh`：server 独立验收，支持 health / env / DingTalk authorize / env drift check
- `verify-web-release.sh`：web 独立验收，支持首页探活与关键 env 校验
- `verify-release.sh`：聚合验收入口，支持 `all|server|web`
- `rollback-release.sh`：标准回滚入口，支持 `server|web|all`
- `status.sh`：查看 current release、组件状态、release summary 与 recent logs
- `sync-to-runtime.sh`：把 repo 中脚本、发布配置校验器和模板同步到 `/opt/skillhub`
- `apply-firewall-hardening.sh`：维护 `DOCKER-USER` 生产端口访问规则，包括限制 `18088` 仅允许 `10.0.0.0/8`
- `recommend-external-skill.sh`：下载外部 skill bundle，发布缓存到本地 SkillHub，校验可下载后按 `namespace/slug` 加入推荐列表

生产 `18088` 访问限制、Secure Cookie、代理联动、验证和回滚流程见 [production-access-hardening-2026-09-18.md](./production-access-hardening-2026-09-18.md)。

## Compose 与生产 `docker run` 的边界

仓库中的两个 Compose 文件用途不同：

- `docker-compose.yml`：本地源码开发依赖。它只启动 PostgreSQL、Redis、MinIO 和源码 scanner，server/web 由宿主机上的 Maven/Vite 进程运行。
- `compose.release.yml`：Quickstart/单机交付。它使用已构建的 server/web 镜像，启动完整容器栈，供用户在单机上运行。
- Quickstart 使用 `.env.quickstart`，面向本地 `http://localhost`，并允许 `SESSION_COOKIE_SECURE=false`。
- 生产配置使用 `.env.release`，面向 HTTPS，必须保持 `SESSION_COOKIE_SECURE=true`；推荐 S3/OSS。迁移期间允许单机使用 `SKILLHUB_STORAGE_PROVIDER=local`，但必须备份 Docker volume，且不能扩展为多节点或多副本；两者不能互换。

当前生产发布不直接执行根目录的 `compose.release.yml`。生产机的应用容器由 `/opt/skillhub/ops/release-lib.sh` 中的 `docker run` 创建；`ops/templates/compose.release.yml.tpl` 生成每次 release 的配置快照，用于审计、回滚和追踪。远端 `deploy-release.sh` 在 plan/apply 前会调用同步到 `/opt/skillhub/ops/validate-release-config.sh` 的配置校验器。三套配置的关键安全变量由 `make test-ops` 校验一致性。

## 外部技能推荐脚本

`recommend-external-skill.sh` 的边界是：**外部 URL 先导入成本地可下载技能，再调用推荐 API**。推荐 API 本身仍只管理已缓存技能，不直接接收外部链接。

示例：

```bash
SKILLHUB_ADMIN_USERNAME=admin SKILLHUB_ADMIN_PASSWORD='***' \
  ./ops/recommend-external-skill.sh https://example.com/skill.zip \
    --base-url http://127.0.0.1:8080 \
    --namespace global \
    --title '推荐技能' \
    --summary '技能简介' \
    --reason '推荐理由' \
    --badge '推荐' \
    --priority 100
```

脚本会依次执行：下载 zip、登录、发布到 SkillHub、可选写入来源镜像记录（`remote_mirror_record`）、校验版本为 `PUBLISHED`、校验下载入口可访问、调用 `POST /api/v1/admin/recommendations/{namespace}/{slug}`。

如需把安全扫描 `FAIL` 的外部包留给管理员审核，可传 `--allow-failed-review`。此时服务端会在同步安全审计可用的前提下创建 `PENDING_REVIEW` 版本和审核任务；脚本会写入来源镜像记录后停止，不校验下载、不创建推荐位。

## 同步方式

在 repo 中修改脚本后，用：

```bash
cd /home/ubuntu/bjzgcai/skillhub
./ops/sync-to-runtime.sh
```

它会：
- 同步 `ops/*.sh` 到 `/opt/skillhub/ops/`
- 同步 `ops/templates/*` 到 `/opt/skillhub/releases/templates/`
- 设置执行权限
- 对关键脚本执行 `bash -n`

## 一键发布到生产

从 dliang-dev 的 repo 源码目录执行：

```bash
cd /home/ubuntu/bjzgcai/skillhub
SKILLHUB_PROD_HOST=ubuntu@prod.example.com \
  ./ops/release-to-prod.sh --component all
```

默认是 dry-run：构建镜像、传到生产机、执行生产 release plan，但不会替换线上容器。确认 plan 后显式加 `--apply`：

```bash
./ops/release-to-prod.sh --host ubuntu@prod.example.com --component all --apply
```

`release-to-prod.sh` 在执行远端 plan/apply 前会自动同步当前仓库的
`ops/*.sh`、`ops/templates/*` 和 `scripts/validate-release-config.sh` 到
生产机 `/opt/skillhub`，并在远端通过 `bash -n` / `sh -n` 校验。生产发布不再依赖
人工先执行本机的 `sync-to-runtime.sh`；后者仍用于本机直接维护 `/opt/skillhub` 的场景。

常用参数：

```bash
./ops/release-to-prod.sh --host ubuntu@prod.example.com --component web --apply
./ops/release-to-prod.sh --host ubuntu@prod.example.com --component server --tag prod-local-20260623T020000Z-weekly --apply
./ops/release-to-prod.sh --host ubuntu@prod.example.com --component all --skip-build --skip-transfer --tag prod-local-20260623T020000Z-weekly --apply
```

脚本边界：
- repo 侧负责构建 `skillhub-server:<tag>` / `skillhub-web:<tag>` 并 `docker save | ssh docker load` 到生产机。
- 生产机仍由 `/opt/skillhub/ops/deploy-release.sh` 执行 release 切换和失败回滚。
- `--apply` 时要求 Git 工作区干净，避免把未提交代码发到生产。

## 推荐工作流

1. 在 repo 中修改代码或 `ops/` 脚本
2. 本地执行语法检查 / smoke test
3. 如需直接维护当前机器的 `/opt/skillhub`，运行 `./ops/sync-to-runtime.sh`；远程生产发布由下一步自动同步
4. 用 `--host <ssh-target>` 或 `SKILLHUB_PROD_HOST` 提供生产目标，再执行 `./ops/release-to-prod.sh --component <all|server|web>` 生成生产发布 plan
5. 确认 plan、配置校验和脚本同步成功后，执行 `./ops/release-to-prod.sh --host <ssh-target> --component <all|server|web> --apply`
6. 将脚本改动和相关文档一起纳入 Git

## 不要做的事

- 不要把 `/opt/skillhub/ops` 当唯一源码长期手改
- 不要把 `/opt/skillhub/shared/env.release` 或 `secrets.env` 提交到 Git
- 不要把 `releases/`、`current`、真实运行产物纳入仓库

## 后续建议

未来可继续在 release 产物中加入：
- `opsGitCommit`
- 更细的 deploy / verify / rollback 结构化结果
- current 与运行态 drift 的显式判定

### 外部来源标记

导入外部 registry 技能时，应同时标记来源，便于后续按 `source=clawhub` 过滤、审计和追溯：

```bash
SKILLHUB_ADMIN_USERNAME=admin SKILLHUB_ADMIN_PASSWORD='***' \
  ./ops/recommend-external-skill.sh 'https://clawhub.ai/api/v1/download?slug=mineru-document-extractor&version=0.1.30' \
    --base-url http://127.0.0.1:8080 \
    --namespace global \
    --title 'MinerU 文档提取工具' \
    --summary 'PDF、扫描件、Office 文档和网页转 Markdown/HTML/DOCX' \
    --reason '文档解析、OCR、表格/公式提取等场景常用' \
    --badge '推荐' \
    --priority 100 \
    --source-registry clawhub \
    --source-namespace mineru-extract \
    --source-slug mineru-document-extractor \
    --source-version 0.1.30
```
