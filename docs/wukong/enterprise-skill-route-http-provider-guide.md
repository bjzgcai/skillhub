# 企业技能路由服务 - HTTP 接入开发者指南

> 来源：用户提供的图片 OCR 整理。图片标题为「专属技能路由对接说明」，附件名显示为「HTTP Provider 开发者接入指南 v2.md」。

## 这是什么？

专属悟空的「企业技能中心」允许企业自建一个 HTTP 服务，接管用户的技能搜索与推荐。当用户在悟空中输入一条 query 时，悟空会将请求转发到企业配置的 HTTP 服务地址，由企业服务决定返回哪些技能来执行该任务。

简单来说，你需要开发一个 HTTP 接口：接收悟空发来的用户查询信息，返回一组匹配的技能列表。

## 你需要做什么 vs 不需要做什么

### 你需要做的（本文档核心内容）

开发一个 HTTP POST 接口，能够接收悟空发来的 JSON 请求，根据用户的查询意图返回最匹配的技能列表。你需要关注的只有三件事：

- 请求长什么样
- 响应长什么样
- 怎么让推荐更准

### 不需要你做的（管理员在界面上配置即可）

以下配置项均由企业管理员在钉钉管理后台的「技能中心配置」页面完成，开发者无需关心：

- HTTP 服务地址：在「企业技能路由服务（Skill Provider）」中填写你的接口地址
- 鉴权方式：在「鉴权配置」中选择，悟空会自动在请求中附带鉴权信息
- 关闭默认推荐技能：开启后，本地算法推荐的技能不参与路由，仅返回你的服务中的技能
- 携带用户本地技能信息：开启后，每次请求会附带用户已安装的本地技能列表，帮助你做更精准的推荐

## 快速开始

### 最小可用示例

假设你的服务部署在 `https://api.example.com/skill-discover`，下面是一个最简单的对接流程。

悟空发给你的请求（HTTP POST）：

```http
POST https://api.example.com/skill-discover
Content-Type: application/json
```

```json
{
  "keywords": ["周报"],
  "domain": null,
  "contextSummary": null
}
```

你需要返回的响应：

```json
{
  "skills": [
    {
      "id": "weekly-report",
      "name": "weekly-report",
      "display_name": "周报助手",
      "description": "按周汇总项目进展并生成周报",
      "install_locator": {
        "type": "remote_url",
        "url": "https://example.com/skills/weekly-report.zip"
      }
    }
  ]
}
```

就这么简单。悟空收到响应后，会把这些技能展示给用户选择使用。

## 请求格式详解

悟空会以 HTTP POST 方式调用你的接口，请求体为 JSON 格式。

### 基础请求字段

每次请求固定包含以下 3 个字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `keywords` | `string[]` | 用户输入的关键词数组，是最核心的查询信息 |
| `domain` | `string | null` | 可选的领域过滤标识，如 `office`、`engineering` 等 |
| `contextSummary` | `string | null` | 【暂不提供】上下文摘要，描述用户当前对话场景的简要信息 |

示例：

```json
{
  "keywords": ["日报", "周报"],
  "domain": "office",
  "contextSummary": "用户正在讨论本周项目进展"
}
```

### 携带本地技能信息（可选增强）

当管理员在配置页面开启「携带用户本地技能信息」开关后，请求中会额外包含一个 `localSkills` 字段，告诉你用户本地已经安装了哪些技能。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `localSkills` | `object[]` | 用户当前已安装的本地技能快照列表 |

每个 `localSkills[]` 元素的结构：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 本地技能 ID |
| `name` | `string` | 本地技能名称 |
| `enabled` | `boolean` | 该技能是否处于启用状态 |
| `description` | `string` | 技能描述 |
| `source` | `string` | 技能来源标识 |
| `version` | `integer | null` | 已安装版本号，可能为 `null` |

携带本地技能信息的完整请求示例：

```json
{
  "keywords": ["日报"],
  "domain": "office",
  "contextSummary": "用户正在讨论本周项目进展",
  "localSkills": [
    {
      "id": "daily-report",
      "name": "日报助手",
      "enabled": true,
      "description": "自动汇总工作进展并生成日报",
      "source": "BUILT_IN",
      "version": 7
    },
    {
      "id": "weekly-report",
      "name": "周报助手",
      "enabled": false,
      "description": "按周汇总项目进展并生成周报",
      "source": "local",
      "version": null
    }
  ]
}
```

## 响应格式详解

你的接口需要返回一个 JSON 对象，顶层包含一个 `skills` 数组。每个元素代表一个推荐给用户的技能。

### 响应结构规则

- 顶层必须是 JSON 对象（不能直接返回数组）
- 必须包含 `skills` 字段
- `skills` 必须是数组
- 字段名统一使用 `snake_case`（如 `display_name`，不是 `displayName`）

### 技能字段说明

下面按重要程度分为「必填字段」「推荐字段」和「可选字段」三组。

#### 必填字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 技能唯一标识，用于去重和匹配 |
| `name` | `string` | 技能内部名称，安装时写入本地数据库 |
| `install_locator` | `object` | 安装定位器，告诉悟空如何获取这个技能的安装包，详见「安装定位器」章节 |

#### 推荐字段

这些字段虽然不是必填，但强烈建议提供，直接影响用户体验：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `display_name` | `string` | 回退到 `name` | 技能展示名，用户在界面上看到的名称 |
| `description` | `string` | 空 | 技能描述，帮助用户理解技能用途 |
| `icon_url` | `string` | 无 | 技能图标地址 |
| `tags` | `string[]` | `[]` | 标签，用于分类和筛选 |
| `display_description` | `string` | 无 | 技能中心详情页优先展示的描述，比 `description` 更面向用户 |
| `default_query` | `string` | 无 | 技能默认预填问题，用户点击后自动填入的示例 query |
| `preview_images` | `string[]` | `[]` | 技能预览图 URL 列表，在技能详情页展示 |
| `score` | `number` | 无 | 技能相关性分数，用于排序，详见「如何提升推荐准确性」章节 |

#### 可选字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `skill_md` | `string` | 无 | 技能的 Markdown 说明内容 |
| `current_version` | `integer` | 无 | 远端当前版本号 |
| `force_update` | `boolean` | `false` | 是否强制覆盖用户本地已安装的同名技能 |
| `source` | `string` | 无 | 远端来源标识 |
| `is_user_enable` | `boolean` | 无 | 远端用户启用态 |
| `zip_sha256` | `string` | 无 | 安装包 SHA256 摘要，用于完整性校验 |
| `create_time` | `integer` | 无 | 技能创建时间戳 |
| `update_time` | `integer` | 无 | 技能更新时间戳 |
| `auth_required` | `boolean` | `false` | 该技能是否需要用户额外认证 |
| `metadata` | `map<string,string>` | `{}` | 元数据键值对，值必须是字符串 |
| `extension` | `map<string,string>` | `{}` | 扩展字段键值对，值必须是字符串，用于技能详情页展示 |
| `source_type` | `string` | 自动填充 | 来源类型标识 |
| `trust_tier` | `string` | `trusted` | 信任等级 |
| `availability_state` | `string` | `available` | 可用状态 |

## 安装定位器 `install_locator`

每个技能必须告诉悟空如何获取安装包。最常用的方式是提供一个下载地址：

```json
{
  "install_locator": {
    "type": "remote_url",
    "url": "https://example.com/skills/weekly-report.zip"
  }
}
```

`type` 支持以下值：

| `type` | 说明 | 必填字段 |
| --- | --- | --- |
| `remote_url` | 提供技能安装包的下载 URL（最常用） | `url`：下载地址，必须是 `http://` 或 `https://` |
| `cli_command` | 通过本地命令行工具获取安装包 | `command`：命令数组，如 `["installer", "prepare", "--skill", "xxx"]` |

推荐使用 `remote_url`，这是最简单直接的方式。你只需要把技能安装包（zip 格式）托管在一个可下载的地址上即可。

## 完整响应示例

下面是一个包含丰富信息的完整响应示例，展示了技能中心详情页所需的关键字段：

```json
{
  "skills": [
    {
      "id": "daily-report",
      "name": "daily-report",
      "display_name": "日报助手",
      "description": "自动汇总工作进展并生成日报",
      "display_description": "输入今天完成的事项，一键生成结构化日报",
      "default_query": "请根据今天的工作记录生成日报",
      "icon_url": "https://example.com/skills/daily-report/icon.png",
      "preview_images": [
        "https://example.com/skills/daily-report/preview-1.png",
        "https://example.com/skills/daily-report/preview-2.png"
      ],
      "skill_md": "# 日报助手\n\n输入今天完成的事项，输出结构化日报。",
      "tags": ["办公", "日报"],
      "current_version": 7,
      "score": 0.95,
      "source": "enterprise",
      "create_time": 1710000000,
      "update_time": 1720000000,
      "install_locator": {
        "type": "remote_url",
        "url": "https://example.com/skills/daily-report.zip"
      },
      "auth_required": false,
      "metadata": {
        "vendor": "example"
      },
      "extension": {
        "适用场景": "日报、周报、项目复盘"
      }
    }
  ]
}
```

## 如何提升推荐准确性

技能路由的核心价值在于：给用户的 query 匹配到最合适的技能。以下是几个关键策略。

### 1. 善用 `keywords` 做语义匹配

`keywords` 是用户输入经过分词后的关键词数组，是最核心的匹配依据。建议：

- 为每个技能维护一份关键词库（包括同义词、缩写、行业术语）
- 不要只做精确匹配，建议结合语义相似度计算
- 中文场景下注意分词粒度，如「周报」和「每周报告」应该匹配到同一个技能

### 2. 利用 `contextSummary` 理解用户意图

`contextSummary` 提供了用户当前对话的上下文摘要。同样的关键词在不同上下文下可能指向不同技能。例如：

- `keywords: ["报告"]` + `contextSummary: "用户在讨论季度财务数据"`：应该推荐「财务报告」技能
- `keywords: ["报告"]` + `contextSummary: "用户在讨论项目进展"`：应该推荐「项目周报」技能

### 3. 利用 `localSkills` 避免重复推荐

当管理员开启「携带用户本地技能信息」后，你可以知道用户已经安装了哪些技能。建议：

- 同时需要关闭本地技能召回
- 已安装且启用的技能可以降低推荐优先级，避免重复推荐
- 已安装但未启用的技能可以适当提醒用户启用
- 根据用户已安装技能的类型，推断用户偏好，推荐同类别的新技能

### 4. 返回 `score` 字段辅助排序

你可以为每个返回的技能附带一个 `score` 分数，表示该技能与当前 query 的匹配程度。悟空会在你返回的这批结果内部对 `score` 进行归一化处理（映射到 0~1），用于最终排序展示。

- 分数的绝对值不重要，重要的是相对大小关系
- 分数越高的技能排序越靠前
- 如果不返回 `score`，悟空会按你返回的数组顺序展示

### 5. 利用 `force_update` 推送技能更新

当你的技能有重要更新时，可以将 `force_update` 设为 `true`。这样即使用户本地已安装了旧版本，搜索结果中也会展示你的新版本，引导用户更新。默认情况下（`force_update=false`），如果用户本地已有同名技能，搜索结果会优先展示本地版本。

## 关于 `auth_required` 的自动合并

如果管理员在配置页面选择了鉴权方式（非「无鉴权」），悟空会自动将你返回的所有技能标记为需要认证，即使你返回的 `auth_required` 是 `false`。

这意味着你不需要为每个技能单独设置 `auth_required: true`，只要 provider 级别配置了鉴权，所有技能都会自动继承。

## 常见问题

### Q：我的接口需要支持什么 HTTP 方法？

固定为 `POST`，请求体为 JSON 格式。

### Q：响应中字段名用驼峰还是下划线？

统一使用 `snake_case`（下划线命名）。例如 `display_name`，不是 `displayName`。如果你返回了 `displayName`，悟空不会识别它，展示名会回退到 `name` 字段。

### Q：`id` 和 `name` 有什么区别？

`id` 是技能的唯一标识，用于去重和匹配已安装技能。`name` 是技能的内部名称，安装时会写入本地数据库。

### Q：我可以返回多少个技能？

没有硬性限制，但建议根据 query 的匹配程度只返回最相关的技能（通常 3~8 个），避免返回大量低相关性结果影响用户体验。

### Q：`metadata` 和 `extension` 有什么区别？

`metadata` 用于存储技能的元信息（如厂商、版本等），主要供系统内部使用。`extension` 的内容会展示在技能中心的详情页上，面向最终用户。两者的值都必须是字符串类型，不支持嵌套对象。

### Q：接口超时时间是多少？

默认超时为 5 秒。如果你的服务需要较长处理时间，请确保在 5 秒内返回响应，或联系管理员调整超时配置。

### Q：如果我的接口返回错误或超时会怎样？

如果你的接口不可用，悟空会自动回退到本地技能召回。

## 接入检查清单

在正式上线前，请确认以下事项：

- [ ] 接口支持 HTTP POST 方法，接收 JSON 请求体
- [ ] 响应格式正确：顶层是对象，包含 `skills` 数组
- [ ] 每个技能至少包含 `id`、`name`、`install_locator` 三个必填字段
- [ ] 字段名使用 `snake_case` 命名
- [ ] `install_locator.url` 是合法的 `http` / `https` 地址且可下载
- [ ] 接口响应时间控制在 5 秒以内
- [ ] 已根据 `keywords` 实现技能匹配和推荐逻辑
- [ ] （推荐）提供 `display_name` 和 `description` 提升用户体验
- [ ] （推荐）返回 `score` 字段辅助排序
- [ ] （推荐）处理 `localSkills` 避免重复推荐

## OCR 备注

图片分辨率较高但来自长截图，少量细节由上下文校正。若需要 100% 原文一致版本，建议提供原始附件 `HTTP Provider 开发者接入指南 v2.md` 或文档链接。