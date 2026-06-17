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

- `deploy-release.sh`：标准发布入口，支持 `--component web|server|all` 与 `--apply`
- `release-lib.sh`：公共函数库，供 deploy / rollback / status 等复用
- `verify-server-release.sh`：server 独立验收，支持 health / env / DingTalk authorize / env drift check
- `verify-web-release.sh`：web 独立验收，支持首页探活与关键 env 校验
- `verify-release.sh`：聚合验收入口，支持 `all|server|web`
- `rollback-release.sh`：标准回滚入口，支持 `server|web|all`
- `status.sh`：查看 current release、组件状态、release summary 与 recent logs
- `sync-to-runtime.sh`：把 repo 中脚本和模板同步到 `/opt/skillhub`
- `recommend-external-skill.sh`：下载外部 skill bundle，发布缓存到本地 SkillHub，校验可下载后按 `namespace/slug` 加入推荐列表

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

## 推荐工作流

1. 在 repo 中修改 `ops/` 脚本
2. 本地执行语法检查 / smoke test
3. 运行 `./ops/sync-to-runtime.sh`
4. 再用 `/opt/skillhub/ops/...` 在运行目录执行真实发布/验证
5. 将脚本改动和相关文档一起纳入 Git

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
