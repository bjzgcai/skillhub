# 2026-03-31 本地变更汇总

## 范围

仓库：`/home/ubuntu/bjzgcai/skillhub`

本次本地变更主要围绕以下目标展开：

- 接入并增强 ClawHub 远端 registry / remote mirror 能力
- 修复 compat 与 `clawhub` CLI 的兼容问题
- 修复远端 bundle 导入与包校验问题
- 补齐 compat delete / undelete 的真实行为
- 调整 migration 历史策略并完成测试/正式环境验证
- 用更新后的代码替换正式 `skillhub-server-1`，且不丢失原有数据库数据

---

## 一、核心代码变更主题

### 1. Remote registry / remote mirror
新增或补齐了以下能力：

- 远端 registry 访问抽象与 ClawHub 适配器
- 远端 skill 搜索、详情、resolve、download resolve
- on-demand remote mirror ingest
- mirror provenance 记录
- remote mirror bot 初始化

涉及方向包括：
- `RemoteRegistryConfig`
- `RemoteRegistryProperties`
- `RemoteMirrorIngestAppService`
- `RemoteMirrorRecord*`
- `RemoteRegistryClient*`
- `ClawHubRemoteRegistryClient`
- `RemoteMirrorBotInitializer`

### 2. Compat 修复
补齐/修复了 compat 层多个关键点：

- 详情返回中的 moderation 默认值兼容
- skill 版本相关响应 DTO
- delete / undelete 从空实现改为真实 archive / unarchive
- 远端错误包装与兼容层错误处理

涉及方向包括：
- `ClawHubCompatAppService`
- `ClawHubCompatController`
- `ClawHubSkillVersionListResponse`
- `ClawHubSkillVersionResponse`
- `ServiceUnavailableException`
- `TooManyRequestsException`

### 3. 包校验 / 导入安全
补齐了 remote bundle 和包校验链路中的几个关键修复：

- 允许 `.mjs` 文件扩展名
- 为 remote bundle 下载增加原始包大小硬限制
- 对 zip / bundle 导入链路做兼容修复
- 调整 publish 过程中的相关处理逻辑

涉及方向包括：
- `SkillPackagePolicy`
- `SkillPackageArchiveExtractor`
- `SkillPublishService`
- `application.yml`

### 4. 存储兼容性
- `S3StorageService` 增加 presigner 的 path-style 配置支持

### 5. Migration 调整
当前采用的 migration 策略为：

- `V38__remote_mirror_record.sql`
- `V39__drop_security_audit_skill_version_fk.sql`

并放弃了会导致历史策略更混乱的另一种文件编号分配方式。

---

## 二、测试与验证相关变更

新增/更新了多组测试：

- `ClawHubCompatAppServiceTest`
- `ClawHubCompatControllerTest`
- `RemoteMirrorIngestAppServiceTest`
- `ClawHubRemoteRegistryClientTest`

覆盖的主要方向：
- compat fallback
- remote mirror ingest
- delete / undelete 行为
- bundle 大小限制
- 远端 registry 行为

---

## 三、已验证通过的关键结果

### 1. 独立测试环境
曾基于新仓库代码拉起独立测试环境：
- project: `skillhubtest`
- API: `18080`

并验证通过：
- `/actuator/health`
- `/api/v1/skills/obsidian-sync`
- `/api/v1/download?slug=obsidian-sync&version=1.0.0`
- `clawhub install --force obsidian-sync`

### 2. 正式环境替换
已使用更新后的代码构建镜像替换：
- `skillhub-server-1` → `skillhub-server:prod-local-20260330`

且保留了正式环境的：
- PostgreSQL 数据
- Redis
- Web 容器
- 现有数据卷

### 3. 正式环境核心链路验证
已验证通过：
- 健康检查：`/actuator/health`
- skill detail：`/api/v1/skills/obsidian-sync`
- 搜索：`/api/v1/search?q=obsidian-sync`
- 下载：`/api/v1/download?...` → `302`
- CLI 安装：`clawhub install --force obsidian-sync`
- 本地登录：`admin / SkillHub-PoC-2026!`
- token 创建与 token 鉴权安装

### 4. 用户密码操作
已安全重置本地用户 `dliang` 的密码为新的 bcrypt 哈希，并验证登录成功。

---

## 四、当前已知但未立即改代码的问题

### archived + published 状态割裂问题
已定位 `unoccupied-time-query` 出现：
- `skill.status = ARCHIVED`
- 最新 `skill_version.status = PUBLISHED`

当前判断：
- 发布成功路径未显式把 `skill.status` 从 `ARCHIVED` 恢复为 `ACTIVE`

该问题已记录到工作记忆中，后续再决定是否修改代码语义。

---

## 五、提交准备边界

当前已明确：

### 本次准备提交的方向
- 核心代码
- migration
- 测试
- 远端 registry / mirror / compat 相关真实改动

### 本次暂不提交的方向
- `server/Dockerfile.dev`
- 文档目录 `docs/clawhub-mirror/`（将迁移到外部报告目录）

---

## 六、附：报告文档迁移说明

原先位于：
- `docs/clawhub-mirror/`

的报告类文档，将迁移到：
- `/home/ubuntu/.openclaw/workspace/skillhub/REPORTS/`

用于与仓库内核心代码文档分离。
