# DingTalk SSO 变更清单与 Code Review 结论

## 一、变更清单

本次钉钉 SSO 接入相关的核心变更如下。

### 1. 配置与部署

#### `.env.release.example`
新增 DingTalk SSO 配置项：

- 后端：
  - `SKILLHUB_AUTH_DINGTALK_ENABLED`
  - `SKILLHUB_AUTH_DINGTALK_DISPLAY_NAME`
  - `SKILLHUB_AUTH_DINGTALK_APP_KEY`
  - `SKILLHUB_AUTH_DINGTALK_APP_SECRET`
  - `SKILLHUB_AUTH_DINGTALK_CORP_ID`
  - `SKILLHUB_AUTH_DINGTALK_AGENT_ID`
  - `SKILLHUB_AUTH_DINGTALK_REDIRECT_URI`
  - `SKILLHUB_AUTH_DINGTALK_REQUIRE_CORP_MEMBERSHIP`
  - `SKILLHUB_AUTH_DINGTALK_AUTO_PROVISION_USER`
  - `SKILLHUB_AUTH_DINGTALK_AUTO_LOGIN_IN_DINGTALK`
- 前端：
  - `SKILLHUB_WEB_AUTH_DINGTALK_ENABLED`
  - `SKILLHUB_WEB_AUTH_DINGTALK_PROVIDER`
  - `SKILLHUB_WEB_AUTH_DINGTALK_AUTO`
  - `SKILLHUB_WEB_AUTH_DINGTALK_CORP_ID`

#### `compose.release.yml`
将钉钉配置透传到 release 环境的 `server` 和 `web` 容器。

#### `docker-compose.staging.yml`
将钉钉配置透传到 staging 环境的 `server` 和 `web` 容器。

---

### 2. 后端控制器

#### `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/DingTalkAuthController.java`
主要变更：

- 增加 `/api/v1/auth/dingtalk/config`
- callback 失败时统一跳回前端 `/login`
- callback 成功时不再跳转到后端 `/dashboard`
- 新增 `buildFrontendRedirect()` 统一拼接前端地址

解决的问题：
- 后端默认登录页误跳转
- 登录成功后落到 `18081/dashboard`

---

### 3. DingTalk 认证客户端

#### `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/dingtalk/DingTalkAuthClient.java`
主要变更：

- 增加 token / userinfo / upstream reject 调试日志
- Browser token 兑换请求补 `refreshToken: ""`
- 兼容解析：`unionId` / `openId` / `openid` / `sub`
- `topapi` 接口改为 query 参数传 `access_token`
- 非 `topapi` 接口继续使用 header token

解决的问题：
- Browser OAuth 联调阶段错误不可见
- token/userinfo 字段解析不完整
- topapi token 传参不符合钉钉旧接口风格

---

### 4. Browser OAuth 发起逻辑

#### `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/dingtalk/DingTalkLoginFlowService.java`
主要变更：

- 授权 URL 增加 `prompt=consent`

配合环境变量：
- browser scope 最终测试对齐为：`openid corpid`

解决的问题：
- 授权 URL 与钉钉官方文档未完全对齐

---

### 5. 身份绑定实体

#### `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/entity/IdentityBinding.java`
主要变更：

- `extraJson` 从 `String` 改为 `Map<String, Object>`
- 增加 `@JdbcTypeCode(SqlTypes.JSON)`

解决的问题：
- PostgreSQL `jsonb` 字段写入失败

---

### 6. 身份绑定持久化

#### `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/IdentityBindingService.java`
主要变更：

- 创建 binding 时写入 `claims.extra()` 到 `extra_json`

解决的问题：
- 钉钉返回的额外身份信息未被正确落库

---

## 二、Code Review 结论

### 总体结论
本次修改方向正确，且已经通过测试环境联调验证，**Browser DingTalk OAuth SSO 已在测试环境打通**。

当前代码从功能层面已经可用，但仍有收尾工作适合继续完善。

---

## 三、Review 发现

### [MAJOR] 1. `DingTalkAuthClient` 仍包含较多联调期兼容逻辑

#### 现状
当前客户端同时处理：

- Browser OAuth token 交换
- Browser userinfo
- topapi query token
- header token
- 多种身份字段兼容解析
- 多层 fallback

#### 风险
- 逻辑分支较多
- 维护成本偏高
- 后续易再次混淆 Browser OAuth 与 H5/免登链路

#### 建议
后续建议拆分方法边界，例如：

- `resolveBrowserIdentity()`
- `resolveInAppIdentity()`
- `callTopApi()`
- `callOpenApiWithUserToken()`

---

### [MAJOR] 2. 联调阶段的 verbose 日志需要清理/降级

#### 现状
目前保留了多处联调用日志：

- browser token response keys
- browser userinfo response keys
- enterprise user detail response keys
- upstream rejected response

#### 风险
- 正式环境日志噪声较大
- 容易暴露过多身份字段结构
- 业务日志与调试日志混在一起

#### 建议
上线前：

- 将字段级日志降到 debug
- 或保留 requestId / provider / errcode 级别日志，移除结构性 info 日志

---

### [MAJOR] 3. 缺少关键回归测试

#### 建议补测点

1. callback 成功时：
   - 应跳转到 `publicBaseUrl + /dashboard`
2. callback 失败时：
   - 应跳转到 `publicBaseUrl + /login`
3. `IdentityBinding.extra_json`：
   - 应能正确以 `jsonb` 存取
4. browser userinfo 字段解析：
   - `unionId/openId/sub` 兼容解析

---

### [MINOR] 4. `IdentityBinding.extraJson` 映射已修正，但建议补充读取验证

#### 原因
当前已完成写入修复，但后续仍应确认：
- 读取侧没有任何地方继续把 `extraJson` 当成纯字符串处理

---

### [MINOR] 5. redirect 逻辑可进一步抽到通用 OAuth 工具层

#### 原因
本次 DingTalk 已实现 `buildFrontendRedirect()`，未来如 GitHub / 其他 SSO 也要支持前后端分离，建议统一抽象。

---

### [MINOR] 6. 配置说明还可再补强

建议后续在文档中明确：

- `SKILLHUB_AUTH_DINGTALK_REDIRECT_URI` 推荐写法
- Browser OAuth 推荐 scope：`openid corpid`
- `SKILLHUB_PUBLIC_BASE_URL` 的职责
- 同源 `/api` 代理的推荐方式

---

## 四、正向评价

### [GOOD] 1. 问题定位过程正确
本次排障是通过逐层日志和行为验证推进，不是盲改，定位路径清晰。

### [GOOD] 2. `jsonb` 映射修复关键且必要
这是本次真正阻塞登录成功闭环的后端数据层 bug，修法与项目现有 JSON 字段风格一致。

### [GOOD] 3. 登录成功 redirect 修正非常关键
这一步让登录闭环真正从“后端成功”变成“用户可用”。

---

## 五、最终结论

### 功能层面
- Browser DingTalk OAuth SSO 已在测试环境打通
- 当前修改具备合并价值

### 工程层面
建议在正式合并前，继续完成：

1. 清理联调日志
2. 补最关键的 2~4 个回归测试
3. 复核配置文档

---

## 六、建议的下一步

1. 清理调试日志和不再需要的联调兼容输出
2. 补充最关键回归测试
3. 形成 Git commit / PR summary
4. 准备正式环境配置核对清单
