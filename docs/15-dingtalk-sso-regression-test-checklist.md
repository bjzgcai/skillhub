# DingTalk SSO 回归测试清单与当前执行结果

本清单聚焦本次钉钉 SSO 接入中最关键、最容易回归的路径，优先用于合并前自测、回归验证和后续正式环境上线前检查。

> 更新日期：2026-04-02  
> 当前状态：**P0 已补齐并通过；P1 核心项已补齐并通过；P2 仍为上线前人工验证项**

---

## 一、当前执行结果总览

### P0：必须覆盖

| 编号 | 测试项 | 当前状态 | 说明 |
|---|---|---|---|
| P0-1 | Browser OAuth callback 成功后跳前端 dashboard | ✅ 已补齐并通过 | 已新增 controller 回归测试，验证成功回调后跳转到前端 `publicBaseUrl/dashboard` |
| P0-2 | Browser OAuth callback 失败后跳前端 login | ✅ 已补齐并通过 | 已新增 controller 回归测试，验证异常场景跳转到前端 `/login` |
| P0-3 | `identity_binding.extra_json` 正确写入/读取 jsonb | ✅ 已补齐并通过 | 已新增 repository 持久化测试，验证 `Map<String,Object>` 可正常落库并读回 |
| P0-4 | Browser userinfo 兼容 `unionId/openId/sub` | ✅ 已补齐并通过 | 已新增 auth client 测试，覆盖 `unionId`、`openId`、`sub fallback` 及缺失时报错 |

### P1：建议补充

| 编号 | 测试项 | 当前状态 | 说明 |
|---|---|---|---|
| P1-5 | 授权 URL 参数完整性 | ✅ 已补齐并通过 | 已验证 `client_id`、`redirect_uri`、`response_type=code`、`prompt=consent`、`scope=openid corpid`、`state` |
| P1-6 | callback 兼容 `authCode/code` | 🟡 部分补齐并通过 | 已覆盖仅 `code` 时兼容成功；“同时存在时优先 `authCode`”分支尚可继续补强 |
| P1-7 | Browser OAuth token 兑换请求体对齐文档 | ✅ 已补齐并通过 | 已新增请求体断言，验证 `clientId/clientSecret/code/refreshToken/grantType` |
| P1-8 | `scope=openid corpid` 行为稳定 | ✅ 已补齐并通过 | 已在授权 URL 测试中切换为 `openid corpid` 并验证链路参数正确 |

### P2：上线前人工验证

| 编号 | 检查项 | 当前状态 | 说明 |
|---|---|---|---|
| P2-9 | 前端登录页 DingTalk 入口展示 | ⏳ 待人工验证 | 需在真实页面确认入口可见且无需额外切换 |
| P2-10 | 前端同源 `/api` 代理行为正常 | ⏳ 待人工验证 | 需浏览器侧确认无“网络连接失败” |
| P2-11 | callback 地址与钉钉后台配置完全一致 | ⏳ 待人工验证 | 需核对钉钉后台实际配置值 |
| P2-12 | 正式环境变量完整性检查 | ⏳ 待人工验证 | 需上线前核对所有 DingTalk 相关环境变量 |

---

## 二、P0：必须覆盖的回归测试

### 1. Browser OAuth callback 成功后应跳转到前端 dashboard

#### 用例目标
验证登录成功后不会跳转到后端 `/dashboard`，而是正确跳转到前端 `publicBaseUrl`。

#### 覆盖点
- callback 成功
- session 建立成功
- redirect 指向前端地址

#### 预期
- 成功跳转到：`{publicBaseUrl}/dashboard`
- 不应出现：`http://<api-host>/dashboard`

#### 当前结果
- **已补齐并通过**
- 对应测试：
  - `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/DingTalkAuthControllerTest.java`

#### 风险来源
本次联调曾出现成功后跳到 `18081/dashboard` 并返回 500 的问题。

---

### 2. Browser OAuth callback 失败后应跳转到前端 login

#### 用例目标
验证异常场景不会落到后端 `/login` 或 Spring 默认登录页。

#### 覆盖点
- callback 缺少参数
- state 校验失败
- 上游错误
- 用户绑定失败

#### 预期
- 跳转到：`{publicBaseUrl}/login`
- 可带：`reason=...`
- 不应出现：`http://<api-host>/login`

#### 当前结果
- **已补齐并通过**
- 对应测试：
  - `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/DingTalkAuthControllerTest.java`

#### 风险来源
本次联调早期多次错误跳到后端 `18081/login`。

---

### 3. `identity_binding.extra_json` 应可正确写入/读取 jsonb

#### 用例目标
验证钉钉返回的额外用户信息能正确落库，不再出现 `jsonb` 类型错误。

#### 覆盖点
- `IdentityBinding.extraJson` 写入数据库
- 从数据库读取 `extraJson`
- 字段结构保持一致

#### 预期
- PostgreSQL 不报：
  - `column "extra_json" is of type jsonb but expression is of type character varying`
- 写入成功
- 读取后可获得 `unionId/openId/email/nick` 等字段

#### 当前结果
- **已补齐并通过**
- 对应测试：
  - `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/repository/IdentityBindingRepositoryTest.java`
- 本次还同步补充了 `skillhub-auth` 模块的 H2 test 依赖，以支撑 JPA 持久化测试执行

#### 风险来源
这是本次最关键的后端数据层 bug 之一。

---

### 4. Browser userinfo 字段解析必须覆盖 `unionId/openId/sub` 兼容性

#### 用例目标
验证当钉钉 userinfo 返回不同身份字段时，系统仍能正确识别用户主体。

#### 覆盖点
- 仅返回 `unionId`
- 仅返回 `openId`
- 仅返回 `sub`
- 多字段同时存在时优先级

#### 预期
- 任一有效字段存在时，都可构造出合法的外部身份主体
- 不应无故报 `error.auth.dingtalk.userInfoIncomplete`

#### 当前结果
- **已补齐并通过**
- 对应测试：
  - `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/dingtalk/DingTalkAuthClientTest.java`
- 当前已覆盖：
  - `unionId`
  - `openId`
  - `sub` fallback
  - 全部缺失时报错

#### 风险来源
本次联调中，userinfo 与 token 返回字段一度不稳定，是定位时间最长的点之一。

---

## 三、P1：建议补充的回归测试

### 5. `DingTalkLoginFlowService` 生成的授权 URL 应符合文档要求

#### 覆盖点
- `client_id`
- `redirect_uri`
- `response_type=code`
- `prompt=consent`
- `scope=openid corpid`
- `state`

#### 预期
生成授权 URL 时，参数齐全且可预期。

#### 当前结果
- **已补齐并通过**
- 对应测试：
  - `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/dingtalk/DingTalkLoginFlowServiceTest.java`

---

### 6. callback 优先兼容 `authCode`，并兼容 `code`

#### 覆盖点
- 仅 `authCode`
- 仅 `code`
- 同时存在 `authCode` + `code`

#### 预期
- 优先取 `authCode`
- 缺失时兼容 `code`

#### 当前结果
- **部分补齐并通过**
- 当前已覆盖：
  - 仅 `authCode`
  - 仅 `code`
- 尚未单独补充：
  - `authCode + code` 同时存在时优先 `authCode`

#### 说明
当前代码逻辑已具备优先取 `authCode` 的实现，因此该项剩余工作属于补强型测试，而非阻塞性缺口。

---

### 7. Browser OAuth token 兑换请求体应对齐钉钉文档

#### 覆盖点
- `clientId`
- `clientSecret`
- `code`
- `refreshToken: ""`
- `grantType=authorization_code`

#### 预期
- 请求体符合文档约定
- token 兑换成功

#### 当前结果
- **已补齐并通过**
- 对应测试：
  - `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/dingtalk/DingTalkAuthClientTest.java`
- 当前已直接断言请求体包含上述全部关键字段

---

### 8. Browser OAuth scope 为 `openid corpid` 时，系统行为应稳定

#### 覆盖点
- Browser scope 配置为 `openid corpid`
- token 返回字段变化
- userinfo 获取不受影响

#### 预期
- OAuth 授权成功
- userinfo 可正常获取
- 不因为 scope 调整而影响登录闭环

#### 当前结果
- **已补齐并通过**
- 当前已在授权 URL 测试中切换并验证 `scope=openid corpid`
- 与既有 Browser userinfo 解析测试组合后，可覆盖该配置下的核心行为稳定性

---

## 四、P2：上线前人工验证项

### 9. 前端登录页 DingTalk 入口展示
- 登录页可以直接看到钉钉登录入口
- 不需要用户自行切 tab 才能找到入口
- 当前状态：**待人工验证**

### 10. 前端同源 `/api` 代理行为正常
- 不出现“网络连接失败，请检查网络”
- 不要求浏览器直接访问后端绝对地址
- 当前状态：**待人工验证**

### 11. callback 地址与钉钉后台配置完全一致
- 正确值应为：
  - `http://<api-host>/api/v1/auth/dingtalk/callback`
- 不使用 `/login/oauth2/code/dingtalk`
- 当前状态：**待人工验证**

### 12. 正式环境变量完整性检查
- `SKILLHUB_AUTH_DINGTALK_*`
- `SKILLHUB_WEB_AUTH_DINGTALK_*`
- `SKILLHUB_PUBLIC_BASE_URL`
- `SKILLHUB_WEB_API_BASE_URL`
- 当前状态：**待人工验证**

---

## 五、已新增/更新的测试文件

### 新增
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/DingTalkAuthControllerTest.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/dingtalk/DingTalkAuthClientTest.java`
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/repository/IdentityBindingRepositoryTest.java`

### 更新
- `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/dingtalk/DingTalkLoginFlowServiceTest.java`
- `server/skillhub-auth/pom.xml`（补充 H2 test 依赖）

---

## 六、本轮测试执行结果

本轮已执行并通过：

- `IdentityBindingRepositoryTest` ✅
- `DingTalkLoginFlowServiceTest` ✅
- `DingTalkAuthClientTest` ✅
- `DingTalkAuthControllerTest` ✅

结果：
- **BUILD SUCCESS**

---

## 七、结论

当前回归测试状态可以总结为：

1. **P0 已补齐并通过**
2. **P1 核心项已补齐并通过**
3. **P1 仅剩一个非阻塞补强点**：`authCode + code` 同时存在时优先级分支测试
4. **P2 仍需上线前人工验证**

如果只看当前自动化回归覆盖，本次 DingTalk SSO 联调中最关键、最真实、最容易再次回归的问题，已经基本纳入测试保护范围。
