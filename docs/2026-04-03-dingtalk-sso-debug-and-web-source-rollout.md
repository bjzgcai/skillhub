# 2026-04-03 DingTalk SSO 调试日志上线与 Web Source 筛选发布记录

## 背景

本轮工作的直接触发点有两个：

1. **DingTalk Browser SSO 在正式环境存在偶发现象**：同样的登录入口下，部分尝试会成功登录，部分尝试会跳回登录页面。
2. **Web 端 source 筛选功能需要按仓库最新代码部署到正式环境并验证可用性**。

结合现象判断，DingTalk SSO 问题并不像“配置完全错误导致稳定失败”，更像是 **OAuth 浏览器登录链路中的 session / cookie / callback 入口存在不稳定因素**。

---

## 工作目标

- 在不破坏现网可用性的前提下，为 DingTalk SSO 增加足够的调试日志，帮助定位“偶发回登录页”的真实原因。
- 先做备份，再在正式环境上线可观测版本。
- 使用仓库最新代码重新部署 web 服务。
- 验证 web 侧新增的 **source 筛选** 功能是否可正常使用。

---

## 代码仓与部署环境

- 开发仓库：`/home/ubuntu/bjzgcai/skillhub`
- 正式部署目录：`/opt/skillhub`
- 本次工作分支：`debug/dingtalk-sso-login-flaky`
- 调试日志提交：`4deeb79 chore(auth): add dingtalk sso debug logs`

---

## 备份记录

### 配置与部署备份

- `/home/ubuntu/backups/skillhub-prod-20260403T045649Z`

备份内容包括：
- 正式环境配置文件副本
- 正式环境 compose 文件副本

### 镜像 / 运行工件备份

- `/home/ubuntu/backups/skillhub-images-20260403T050040Z`

备份内容包括：
- 当前运行中的 server / web 镜像 inspect 信息
- 当前线上 server 容器中的 `/app/app.jar` 备份
- 本次调试版候选 JAR 备份

### Web 部署备份

- `/home/ubuntu/backups/skillhub-web-20260403T051802Z`

备份内容包括：
- 原 web 镜像 inspect 信息
- web 容器 nginx 生效配置副本

---

## DingTalk SSO 调试日志改动

### 改动目标

本次没有直接“先猜后修”，而是先把关键链路打通可观测性，确保下一步定位基于真实链路证据。

### 涉及文件

- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/DingTalkAuthController.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/dingtalk/DingTalkLoginFlowService.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/dingtalk/DingTalkLoginService.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/dingtalk/DingTalkAuthClient.java`
- `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/session/PlatformSessionService.java`
- `server/skillhub-app/src/main/resources/application-local.yml`

### 增加的日志覆盖点

#### 1. DingTalk authorize 入口
记录：
- sessionId
- returnTo / sanitizedReturnTo
- state 指纹
- redirectUri
- browserScope
- Host / X-Forwarded-Proto
- User-Agent

#### 2. DingTalk callback 入口
记录：
- callback 收到时的 sessionId
- state 指纹
- authCode / code 是否存在
- error 参数
- fallbackReturnTo
- Host / X-Forwarded-Proto
- 是否带 Cookie Header
- User-Agent

#### 3. state 校验链路
记录：
- expected state 是否存在
- expected / actual state 指纹
- 当前 sessionId
- returnTo
- 当 callback 异常分支触发时，记录“未校验即消费状态”的行为

#### 4. token / userinfo / identity mapping
记录：
- browser token exchange 成功 / 失败
- app token 刷新成功 / 失败
- browser userinfo / in-app userinfo 的关键字段提取情况
- unionId / userId / principal 绑定情况

#### 5. 登录态建立
记录：
- Platform session 建立成功时的 userId
- rotateSessionId 开关
- sessionIdBefore / sessionIdAfter
- authorities

### 安全处理

日志中没有直接输出完整 authorization code / access token / unionId，而是使用了：
- 指纹形式：`prefix...suffix(len=n)`
- 是否存在 / key 集合 / 摘要值

因此日志具备排障价值，同时避免把敏感值完整写入日志。

---

## 构建与测试结果

### 定向测试

执行了与 DingTalk SSO 相关的后端定向测试，结果通过。

测试与构建相关结论：
- `DingTalkAuthControllerTest` 通过
- 相关后端模块构建通过
- 调试版 JAR 构建成功

### 全量后端 app 打包

执行：
- `./mvnw -pl skillhub-app -am clean package -DskipTests`

结果：
- `BUILD SUCCESS`

---

## 正式环境 server 上线方式

### 现状确认

正式环境并非直接拉取 GHCR 镜像，而是使用本地构建镜像运行：

- server：`skillhub-server:prod-local-20260402`
- web：`skillhub-web:prod-local-20260402`

server 容器运行状态确认：
- 单实例
- Session 存储为 Redis
- 当前 profile：`docker`

### 本次 server 上线动作

由于正式目录中的 compose / env 文件存在隐藏文件可见性与运行方式差异，本次采用了**最小侵入方式**完成上线：

1. 构建调试版 JAR
2. 备份现网容器内 `app.jar`
3. 将调试版 JAR 复制进 `skillhub-server-1:/app/app.jar`
4. 重启 `skillhub-server-1`
5. 通过 `http://127.0.0.1:8080/actuator/health` 验证服务恢复

### 上线结果

- server 成功重启
- health check 正常
- 调试日志已能在现网容器日志中看到

---

## 已观察到的关键现网信息

### 当前正式配置中的关键值

- `SKILLHUB_PUBLIC_BASE_URL=http://10.1.132.6`
- `SKILLHUB_AUTH_DINGTALK_REDIRECT_URI=http://10.1.132.6:8080/api/v1/auth/dingtalk/callback`
- `SESSION_COOKIE_SECURE=false`
- Spring Session 使用 Redis
- 当前 server 为单实例

### 已确认的事实

1. **多实例会话不一致不是当前首要嫌疑**
   - 现在是单实例 server + Redis session。

2. **登录入口不一致值得重点关注**
   - 用户访问 web 站点使用 `http://10.1.132.6`（80 端口）
   - DingTalk callback 却回到 `http://10.1.132.6:8080/api/v1/auth/dingtalk/callback`
   - 即前端入口和 OAuth callback 入口不是同一个外部入口。

3. **SESSION cookie 当前为 `SameSite=Lax`**
   - 在标准顶层 GET 跳转中理论上可能可用
   - 但第三方登录和内嵌浏览器场景下仍然可能引起不稳定回传

4. **线上此前已经出现过 `/api/v1/auth/me` 401**
   - 这说明“callback 成功后前端再拉当前用户信息失败”也是一个真实嫌疑点

---

## 调试日志样例验证

已经通过本地请求对现网调试日志进行了样例验证。

### authorize 样例

可见日志：
- `DingTalk authorize endpoint hit`
- `DingTalk authorize started`

并且响应头中可见：
- `Set-Cookie: SESSION=...; HttpOnly; SameSite=Lax`
- `Location: https://login.dingtalk.com/oauth2/auth?...&state=...`

### callback error 分支样例

模拟 callback error 后，可见日志：
- `DingTalk callback received`
- `DingTalk flow state consumed without validation`

说明调试链路已经成功上线，可用于后续真实失败样本分析。

---

## 当前对 DingTalk SSO 问题的判断

### 当前最高优先怀疑项

#### 1. callback 请求没有稳定带回 authorize 时建立的 SESSION cookie
若发生此问题，会表现为：
- state 校验失败
- 最终跳回登录页

#### 2. callback 后虽然成功建立 session，但前端后续请求 `/api/v1/auth/me` 时没有带上同一份 SESSION
若发生此问题，会表现为：
- callback 已成功
- 前端回站点后仍认为未登录
- 页面表现依然像“跳回登录页”

#### 3. 80 端口 web 入口与 8080 端口 callback 入口混用导致登录态链路不稳定
当前这是非常值得继续验证的方向。

### 当前暂不优先的嫌疑项

- DingTalk appKey / appSecret 完全配置错误
- DingTalk OAuth 功能整体不可用
- 多实例会话漂移

这些问题若存在，通常会更偏向**稳定失败**，与“偶发成功、偶发失败”的症状不完全匹配。

---

## Web 最新代码部署

### 目标

按用户要求，将仓库最新代码部署到正式环境 web 服务，并验证 source 筛选功能。

### 构建结果

基于仓库最新代码构建了新镜像：
- `skillhub-web:prod-local-20260403-latestweb`

### 部署动作

1. 备份旧 web 镜像与 nginx 配置
2. 将仓库最新代码同步到 `/opt/skillhub`
3. 使用 `web/Dockerfile` 构建新镜像
4. 替换当前 `skillhub-web-1` 容器
5. 保持现网等价运行参数：
   - `SKILLHUB_API_UPSTREAM=http://server:8080`
   - `SKILLHUB_PUBLIC_BASE_URL=http://10.1.132.6`
   - DingTalk web runtime config 保持开启

### 部署验证

已确认：
- `nginx-health` 正常
- 首页返回 `200`
- `runtime-config.js` 生效
- DingTalk runtime config 存在且值正确

---

## Source 筛选验证结果

用户在 web 更新后完成了 source 筛选测试，并确认：

- **筛选正常**

这意味着：
- 本轮 web 最新代码部署成功
- source 筛选相关前端能力在当前正式环境下可用

---

## 后续建议

### 对 DingTalk SSO 的下一步建议

建议用户继续在正式环境做：
- 至少 1 次成功登录样本
- 至少 1 次失败登录样本

然后结合新日志重点对比：
- authorize 的 sessionId / state
- callback 的 sessionId / cookieHeaderPresent
- state 校验结果
- Platform session establish 日志
- callback 后是否紧跟 `/api/v1/auth/me` 401

### 若日志证明确为入口 / cookie / session 问题
建议的修复优先级：

1. **优先统一 DingTalk callback 到对外公开入口**
   - 倾向改成：`http://10.1.132.6/api/v1/auth/dingtalk/callback`
   - 避免直接暴露 `:8080` 作为浏览器 callback 入口

2. **重新校验 web → server 代理路径与 cookie 回传策略**
   - 确认 callback、回跳页面、后续 `/api/v1/auth/me` 是否在同一入口语义下完成

3. **后续若上 HTTPS，再系统收紧 Cookie 策略**
   - 包括 `Secure` / `SameSite` 等配置

---

## 本轮结论

本轮工作已经完成以下目标：

- 已为 DingTalk SSO 问题建立关键链路调试能力
- 已完成 server 调试版上线并验证健康
- 已完成 web 最新代码部署
- 已验证 source 筛选在正式环境中可用

当前最重要的未完成项不是“继续盲修”，而是：

> 利用已上线的调试日志，抓到一次真实失败链路，确认问题究竟发生在 state 校验、session 建立，还是 callback 之后的前端登录态读取阶段。

在拿到真实失败样本后，再做最小修复会更稳妥。
