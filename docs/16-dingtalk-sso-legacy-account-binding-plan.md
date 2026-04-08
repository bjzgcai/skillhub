# 钉钉 SSO 接入后旧账户绑定方案说明

## 一、背景

在 SkillHub 接入钉钉 SSO 后，平台会新增一种外部身份登录方式：

- `provider_code = dingtalk`
- `subject = unionId`（当前实现中优先使用 `unionId` 作为外部身份主体）

如果系统在用户首次钉钉登录时找不到已有的 DingTalk identity binding，默认行为通常是：

1. 识别为一个新的外部身份
2. 新建一个新的平台用户 `user_id`
3. 将该 DingTalk identity 绑定到这个新用户

这会带来一个风险：

> 老 SkillHub 账户下已经存在的 skill、角色、namespace 成员关系、历史记录等业务资产，不会天然跟到这个新用户上。

因此，在正式启用钉钉 SSO 作为主入口甚至唯一入口之前，必须先考虑：

- 如何让钉钉身份与旧 SkillHub 账户正确关联
- 如何避免因为新建用户导致历史资产“看起来丢失”

---

## 二、当前系统中的绑定方式

当前系统中，DingTalk 身份与 SkillHub 用户的绑定关系核心为：

```text
dingtalk + unionId -> user_id
```

在数据库中主要体现为 `identity_binding`：

- `provider_code = dingtalk`
- `subject = unionId`
- `user_id = SkillHub 平台内部用户 ID`

因此，真正需要建立的是：

> **钉钉 unionId 到旧 SkillHub user_id 的映射关系**

而不是简单依赖邮箱、手机号或显示名。

---

## 三、为什么不能简单依赖邮箱自动映射

在实际场景中，旧账户邮箱与钉钉登记邮箱可能不一致。

例如：
- SkillHub 历史账户使用的是旧邮箱
- 钉钉企业账号中登记的是另一个邮箱
- 或者历史账户根本没有可靠邮箱信息

这会导致：

- 仅按邮箱匹配容易错绑
- 无法唯一定位用户
- 将钉钉身份错误绑定到别人的旧账户
- 进一步导致 skill / 权限 / 历史记录归属混乱

因此，在邮箱不一致的场景下：

## 结论
**不建议使用邮箱自动映射作为正式绑定主策略。**

---

## 四、推荐方案：管理员预绑定

对于用户量不大的系统，最稳妥的方案是：

# 管理员预绑定 `unionId -> 旧 user_id`

也就是由管理员先准备一张映射表，把每个钉钉用户的 `unionId` 和旧 SkillHub `user_id` 对应起来，再批量导入为 `identity_binding`。

这样做的好处是：

1. 用户第一次钉钉登录时，系统能直接命中旧用户
2. 不会新建重复用户
3. 不需要后续再做复杂的账号合并
4. 老账户名下的 skill、权限、审核记录、namespace 关系等保持不变

### 这条路的本质
不是“迁移业务资产”，而是：

> **在用户第一次正式使用钉钉登录前，提前把钉钉身份挂到旧 user_id 上。**

---

## 五、为什么不推荐“先登录生成新用户，再做 merge”

系统中虽然已经存在 `AccountMergeService`，可以合并：

- identity_binding
- api_token
- user_role_binding
- namespace_member
- local_credential

但通过代码和数据库结构检查后发现：

## 当前 merge 逻辑未覆盖全部业务资产
例如业务表中仍然广泛存在对 `user_id` / `owner_id` / `created_by` 的直接依赖，包括：

- `skill.owner_id`
- `skill.created_by`
- `skill_version.created_by`
- `skill_search_document.owner_id`
- `review_task.submitted_by`
- `promotion_request.submitted_by`
- `skill_star.user_id`
- `skill_rating.user_id`
- `skill_tag.created_by`
- `skill_label.created_by`
- `label_definition.created_by`
- `namespace.created_by`
- `profile_change_request.user_id`
- `profile_change_request.reviewer_id`
- `user_notification.user_id`
- `notification_preference.user_id`
- `audit_log.actor_user_id`

这意味着：

### 如果先创建新钉钉用户，再做现有 merge
- auth 层账户关系可以合并
- 但业务层资产不一定都能自动迁过来
- 用户可能出现“老 skill 不见了”“历史记录不完整”等问题

## 因此结论
**对于正式切换到钉钉 SSO 的场景，更推荐预绑定，而不是先造新用户再 merge。**

---

## 六、本地 admin 是否保留

即使正式启用钉钉 SSO，也建议：

# 至少保留 1 个本地兜底 admin

原因：
- 钉钉配置可能出错
- 回调地址可能异常
- identity binding 批量导入可能有误
- 管理员角色可能配置错误
- 紧急情况下仍需要一个不依赖外部身份的救援入口

### 推荐策略
- 日常使用主入口切换到钉钉 SSO
- 但保留一个本地 break-glass admin 账号
- 正式切换稳定运行后，再评估是否收窄本地登录入口

---

## 七、管理员权限是否可以随绑定表一起指定

可以。

在旧账户绑定映射表中，可以增加一列，用于标记某些钉钉用户在绑定完成后需要被授予平台超级管理员权限。

例如增加列：

- `grant_admin`

建议取值：
- `true`
- `false`

这样导入时可以分两步：

1. 建立 `identity_binding`
2. 若 `grant_admin = true`，则为对应 `user_id` 补充平台 admin 角色

### 重要原则
- 钉钉身份绑定 与 平台管理员授权 是两件事
- 可以一起批量处理，但逻辑上建议分开执行与校验

---

## 八、推荐映射表格式

推荐采用如下 CSV：

```csv
unionId,user_id,display_name,skillhub_email,dingtalk_email,grant_admin,remark
```

字段说明：

### `unionId`
- 必填
- 钉钉用户的唯一身份标识
- 将写入 `identity_binding.subject`

### `user_id`
- 必填
- 旧 SkillHub 账户的内部用户 ID

### `display_name`
- 建议填写
- 用于人工核对，不作为最终绑定主键

### `skillhub_email`
- 可选
- 用于核对旧账户信息

### `dingtalk_email`
- 可选
- 用于核对钉钉侧信息

### `grant_admin`
- 可选
- 标记是否在绑定后赋予平台超级管理员权限
- 建议使用 `true/false`

### `remark`
- 可选
- 备注说明，例如：
  - 邮箱不一致，人工确认绑定
  - 管理员账户
  - 核心用户第一批导入

---

## 九、推荐实施步骤

### 步骤 1：先部署最新钉钉 SSO 代码到正式环境
正式环境必须先具备已验证通过的 DingTalk SSO 主链路，包括：

- callback 正确
- Browser OAuth 可用
- userinfo 可用
- `identity_binding.extra_json` 的 `jsonb` 映射已修复
- 登录成功后 redirect 正确回前端

### 步骤 2：确认正式环境 DingTalk 配置完整
包括：

- `SKILLHUB_AUTH_DINGTALK_*`
- `SKILLHUB_WEB_AUTH_DINGTALK_*`
- `SKILLHUB_PUBLIC_BASE_URL`
- Browser OAuth scope（推荐 `openid corpid`）
- 钉钉后台回调地址

### 步骤 3：备份正式环境关键表
至少建议导出：

- `user_account`
- `identity_binding`
- `user_role_binding`
- `namespace_member`

如条件允许，也建议备份 skill 相关业务表。

### 步骤 4：准备映射表
由管理员提供：

- `unionId`
- `user_id`
- 以及辅助核对字段

### 步骤 5：先做 dry-run 校验
导入前必须先检查：

1. `user_id` 是否存在
2. `user_id` 是否 ACTIVE
3. `unionId` 是否已存在绑定
4. `provider_code + subject` 是否冲突
5. `grant_admin` 是否会造成角色重复或异常

### 步骤 6：确认后再正式写入
写入/更新：

- `identity_binding`
- 必要时 `user_role_binding`

### 步骤 7：小批量验证
建议先选择：

- 1 个管理员
- 1～3 个核心老用户
- 1 个历史 skill 较多的老用户

先验证成功后，再批量导入全量用户。

---

## 十、开发工作量评估

如果采用：

# “管理员给映射表，系统批量预绑定”

则工作量明显小于做完整自动映射或大规模 merge 迁移。

### 如果做最小可用版
- 通过脚本 / SQL / 管理后台接口导入映射
- 批量建立 `identity_binding`
- 按需补 admin 角色

### 评估
- **0.5 ～ 1.5 天** 可落地第一版

这远低于：
- 自动邮箱映射 + 安全校验
- 或“先建新用户再迁全部业务资产”的大改造方案

---

## 十一、最终结论

### 1. 旧账户绑定的最佳方案
**不是邮箱自动映射，也不是先造新用户再 merge。**

### 2. 最稳妥方案
**由管理员提供 `unionId -> 旧 user_id` 映射表，预先建立 DingTalk identity binding。**

### 3. 这样做的最大收益
- 用户第一次钉钉登录时直接进入旧账号
- 不产生重复用户
- 不需要迁移 skill / review / notification 等业务资产
- 风险最低，实施成本最低

### 4. 正式切换时仍建议保留一个本地 admin
用于钉钉配置失效或导入异常时的救援入口。

---

## 十二、建议的下一步

1. 部署最新正式环境代码
2. 整理正式环境用户清单
3. 收集第一批钉钉 `unionId -> user_id` 映射关系
4. 进行 dry-run 校验
5. 小批量导入并验证
6. 再决定是否切换为钉钉唯一主入口
