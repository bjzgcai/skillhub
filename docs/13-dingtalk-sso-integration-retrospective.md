# 钉钉 SSO 接入复盘（测试环境）

## 背景

本次工作目标是在 **SkillHub** 中打通 **钉钉网页登录（Browser OAuth SSO）**，并在不影响现有服务部署的前提下完成测试验证。

测试环境采用独立隔离部署：

- Web：`http://10.1.132.6:18000`
- API：`http://10.1.132.6:18081`
- 回调地址：`http://10.1.132.6:18081/api/v1/auth/dingtalk/callback`

现网 SkillHub 服务未被停止、覆盖或复用。

---

## 一、接入过程中遇到的问题

### 1. 前端登录页没有显示 DingTalk 入口

#### 现象
- 登录页看不到钉钉登录按钮。
- 用户误以为前后端配置未生效。

#### 原因
- 钉钉入口被隐藏在 OAuth tab 中。
- 默认展示的是密码登录 tab。
- 前端主视图没有显式展示企业 SSO 入口。

#### 处理办法
- 在登录页主区域直接渲染 DingTalk 登录入口。
- 当钉钉登录可用时，默认切换到 OAuth tab。

#### 结果
- 登录页可以直接看到钉钉入口。

---

### 2. 前端出现“网络连接失败，请检查网络”

#### 现象
- 登录页或登录按钮点击后前端提示网络错误。

#### 原因
- 前端 runtime-config 中将 API base 配成了绝对地址：`http://10.1.132.6:18081`。
- 浏览器直接跨域访问后端，导致网络访问与代理路径不稳定。

#### 处理办法
- 将前端 API 调用切换为同源 `/api` 代理。
- 不再强制前端直连绝对 API 地址。

#### 结果
- 网络连接失败问题消失。
- 前端请求统一通过 Web 侧代理进入后端。

---

### 3. 钉钉 callback 路径最初判断错误

#### 现象
- 用户最初在钉钉后台配置了 `/login/oauth2/code/dingtalk`。
- 登录完成后出现异常跳转或无法进入正确处理逻辑。

#### 原因
- 当前项目并未使用 Spring 默认 OAuth2 回调链路。
- 实际处理逻辑由自定义控制器完成：
  - `/api/v1/auth/dingtalk/callback`

#### 处理办法
- 阅读 `DingTalkAuthController`，确认真实回调入口。
- 将钉钉后台回调地址修正为：
  - `http://10.1.132.6:18081/api/v1/auth/dingtalk/callback`

#### 结果
- callback 请求能够稳定命中后端控制器。

---

### 4. 回调失败后跳到了后端默认登录页

#### 现象
- 用户完成钉钉授权后，页面落到后端 `18081/login`。
- 页面显示 Spring Security 默认 OAuth 登录页。

#### 原因
- callback 异常时，后端 fallback redirect 指向了相对路径 `/login`。
- 相对路径在后端端口上展开，落到了错误页面。

#### 处理办法
- 将失败场景下的重定向统一改为前端地址：
  - `http://10.1.132.6:18000/login`
- 保持错误时也回到前端登录页，而不是后端默认页。

#### 结果
- callback 异常时，用户回到前端登录页。
- 不再跳转到后端默认登录页。

---

### 5. `/api/v1/auth/dingtalk/config` 缺失

#### 现象
- 前端访问钉钉配置接口时报错。
- 页面逻辑无法稳定读取钉钉登录配置。

#### 原因
- 前端依赖该接口，但后端控制器最初没有提供实现。

#### 处理办法
- 在 `DingTalkAuthController` 中补充 `/config` 接口。
- 输出 enabled、corpId、agentId、redirectUri 等前端所需信息。

#### 结果
- 前端可通过统一配置接口获取钉钉登录能力状态。

---

### 6. 前端 runtime-config 未注入钉钉变量

#### 现象
- 即使后端已启用钉钉，前端仍无法正确展示或识别 DingTalk 配置。

#### 原因
- `30-runtime-config.sh` 最初只替换了少数基础变量。
- 钉钉相关变量未被 envsubst 注入。

#### 处理办法
- 扩展 entrypoint 脚本，注入钉钉相关变量，例如：
  - `SKILLHUB_WEB_AUTH_DINGTALK_ENABLED`
  - `SKILLHUB_WEB_AUTH_DINGTALK_PROVIDER`
  - `SKILLHUB_WEB_AUTH_DINGTALK_AUTO`
  - `SKILLHUB_WEB_AUTH_DINGTALK_CORP_ID`

#### 结果
- 前端 runtime-config 能正确感知 DingTalk 配置。

---

### 7. Browser OAuth 与 H5 / 免登 code 链路混淆

#### 现象
- 一度尝试使用 callback 返回的 browser OAuth code 去调用：
  - `POST /topapi/v2/user/getuserinfo`
- 钉钉返回：
  - `不存在的临时授权码`

#### 原因
- browser OAuth callback 的 code，不等同于 H5 / JSAPI / 免登场景中的临时授权码。
- 两种 code 类型不能混用。

#### 处理办法
- 放弃将 browser callback code 直接用于 `topapi/v2/user/getuserinfo`。
- 回归浏览器 OAuth 标准流程：
  1. callback 获取 `authCode`
  2. `/v1.0/oauth2/userAccessToken`
  3. 使用用户 token 调用“获取用户通讯录个人信息”接口

#### 结果
- 链路方向纠正，避免继续在错误接口上消耗时间。

---

### 8. Browser OAuth token 能拿到，但最初 userinfo 获取失败

#### 现象
- `/v1.0/oauth2/userAccessToken` 成功返回：
  - `expireIn`
  - `accessToken`
  - `refreshToken`
- 但后续用户身份接口调用失败或无法拿到所需字段。

#### 原因
- Browser userinfo 接口与 scope / 调用方式未完全对齐。
- 早期实现中 scope 仅为 `openid`，且授权 URL 未补 `prompt=consent`。

#### 处理办法
- 按文档对齐 Browser OAuth 请求：
  - `prompt=consent`
  - `scope=openid corpid`
- 继续使用用户 token 调 Browser userinfo 接口。
- callback 中优先读取 `authCode`，并兼容 `code`。

#### 结果
- Browser userinfo 成功返回字段：
  - `nick`
  - `unionId`
  - `openId`
  - `mobile`
  - `stateCode`
  - `visitor`
  - `email`
- 说明钉钉用户身份信息已成功获取。

---

### 9. `identity_binding.extra_json` 的 `jsonb` 映射错误

#### 现象
- callback 已获取用户身份，但在绑定账户时数据库插入失败。
- PostgreSQL 报错：
  - `column "extra_json" is of type jsonb but expression is of type character varying`

#### 原因
- `identity_binding.extra_json` 在数据库中是 `jsonb`。
- 但 JPA 实体 `IdentityBinding.extraJson` 最初被映射为 `String`。

#### 处理办法
- 将实体字段改为：
  - `Map<String, Object>`
- 使用 Hibernate JSON 映射：
  - `@JdbcTypeCode(SqlTypes.JSON)`
- 保存 binding 时，把 `claims.extra()` 真实写入 `extra_json`。

#### 结果
- 数据库绑定记录可以正确保存。
- 不再因 `jsonb` 类型不匹配而失败。

---

### 10. 登录成功后跳转到了后端 `18081/dashboard`

#### 现象
- 登录已成功，但浏览器被带到了：
  - `http://10.1.132.6:18081/dashboard`
- 页面返回 500。

#### 原因
- 登录成功后的 redirect 仍使用相对路径 `/dashboard`。
- 路径被后端服务解释，跳转到了后端端口，而不是前端站点。

#### 处理办法
- 修改成功登录后的 redirect 逻辑。
- 将成功目标地址拼到前端 `publicBaseUrl` 下：
  - `http://10.1.132.6:18000/dashboard`

#### 结果
- 登录成功后进入前端 dashboard。
- Browser DingTalk SSO 在测试环境完整闭环打通。

---

## 二、过程中尝试过但最终证明不合适/不成立的方案

### 1. 使用 browser callback code 直接调用 `topapi/v2/user/getuserinfo`
- 结果：失败
- 原因：callback 返回的是 OAuth 授权码，不是免登接口要求的临时授权码
- 结论：不能混用 Browser OAuth code 与 H5/免登 code

### 2. 认为 callback URL 没有命中
- 结果：错误判断
- 实际日志已证明 callback 请求稳定命中 `/api/v1/auth/dingtalk/callback`
- 结论：问题不在 callback 是否到达，而在 callback 内部后续处理

### 3. 认为是 scope 完全没有带
- 结果：不成立
- 实际最初就带了 `openid`
- 但为了与官方文档更对齐，后续升级为 `openid corpid`

---

## 三、最终采用的正确链路

### Browser OAuth 登录链路
1. 前端进入 DingTalk 登录入口
2. 跳转到授权地址：
   - `https://login.dingtalk.com/oauth2/auth?...&response_type=code&prompt=consent&scope=openid%20corpid`
3. 钉钉回调：
   - `/api/v1/auth/dingtalk/callback`
4. 后端使用 `authCode` / `code` 调用：
   - `/v1.0/oauth2/userAccessToken`
5. 获取用户 token 后调用 Browser userinfo 接口
6. 拿到 `unionId/openId/email/nick` 等用户身份信息
7. 建立/更新本地 identity binding
8. 创建会话
9. 重定向到前端 `dashboard`

---

## 四、本次联调最终结论

### 1. 钉钉网页登录 SSO 已在测试环境打通
测试环境地址：
- Web：`http://10.1.132.6:18000`
- API：`http://10.1.132.6:18081`

### 2. 关键成功条件
- callback 地址必须使用项目自定义入口：
  - `http://10.1.132.6:18081/api/v1/auth/dingtalk/callback`
- Browser OAuth 与 H5/免登 code 不能混用
- Browser OAuth 推荐参数：
  - `prompt=consent`
  - `scope=openid corpid`
- 前端必须显式展示 DingTalk 登录入口
- 登录成功后的跳转必须返回前端地址，而不是后端服务地址

### 3. 这次真正最耗时的原因
不是单点故障，而是一串串联问题：
- 前端入口
- runtime-config 注入
- API 访问方式
- callback 路径
- Browser OAuth 与免登 code 混淆
- userinfo 接口对齐
- 数据库 `jsonb` 映射 bug
- 登录后跳转地址错误

### 4. 当前状态
- 测试环境已验证成功
- 可在此基础上整理正式环境配置、清理调试日志，并准备提交代码或推进正式部署

---

## 五、建议的后续动作

1. 清理联调阶段临时调试日志
2. 复核 `.env.release.example` 与正式环境变量
3. 将测试环境确认有效的 DingTalk 参数同步到正式部署方案
4. 对本次变更进行 Git 提交并整理变更说明
5. 如需正式上线，优先检查：
   - 前端域名/端口
   - 回调地址
   - Browser scope
   - DingTalk 权限点
   - 反向代理和同源 API 路由
