# 悟空自定义技能中心接入说明

本文档记录 SkillHub 接入悟空「企业技能中心」的最小闭环、协议边界、联调配置和后续演进事项。

当前已实现的是 **专属技能中心页面**：悟空客户端通过 iframe 打开 SkillHub 的 `/wukong` 页面，用户在页面中浏览公开技能，并通过 SkillBridge 调用悟空宿主完成技能安装。

参考资料：

- [钉钉专属技能中心原始文档](./alidoc-exclusive-skillhub.md)
- [Skill Provider HTTP Provider 原始说明](./enterprise-skill-route-http-provider-guide.md)

## 1. 能力边界

悟空配置页里有两个容易混淆的概念：

| 配置项 | 作用 | SkillHub 当前状态 |
| --- | --- | --- |
| 技能中心页面地址 | 悟空用 iframe 打开的企业技能中心 Web 页面。用于浏览、搜索、安装技能。 | 已实现：`/wukong` |
| 企业技能路由服务（Skill Provider） | 悟空运行时向企业后端请求技能召回/路由结果。影响对话或任务执行时“该用哪些技能”。 | 未实现，MVP 先留空 |

简单理解：

- `/wukong` 页面解决“人怎么看见技能、点击安装技能”。
- Skill Provider 解决“悟空在实际执行任务时，系统怎么召回或路由技能”。

因此，当前 MVP 联调时只填写 **技能中心页面地址**，Skill Provider 保持空。

## 2. 悟空侧配置

### 2.1 技能中心页面地址

测试环境可填：

```text
http://10.1.132.6:3002/wukong
```

如果通过 Tailscale/VPN 访问，可填：

```text
http://100.73.189.94:3002/wukong
```

正式环境建议使用固定 HTTPS 域名，例如：

```text
https://skills.zgci.org/wukong
```

不要手动追加 `theme`、`displayMode`、`version` 查询参数。悟空 `ExclusiveSkillHub` 会自动追加：

```text
theme=dark|light
displayMode=fullPage
version=<悟空版本>
```

### 2.2 Skill Provider

当前先不填。

Skill Provider 不是网页地址，也不是 `/wukong`。它应当是一个后端协议接口，用于企业技能路由和技能召回。当前 SkillHub 尚未实现该协议，如果填入前端地址，连通性测试会失败。

### 2.3 相关开关建议

MVP 阶段建议：

| 配置项 | 建议 |
| --- | --- |
| 生效范围 | 按联调需要选择，测试时可部分生效 |
| 鉴权配置 | 不鉴权 |
| 关闭本地技能召回 | 关闭 |
| 携带用户本地技能信息 | 关闭 |

不要在 MVP 阶段打开“关闭本地技能召回”。该开关会让悟空不再使用用户本地技能列表，运行时能力会依赖 Skill Provider；在 Provider 未实现前打开会导致技能召回不可用。

## 3. 前端入口与页面行为

SkillHub 新增路由：

```text
/wukong
```

页面定位：

- 仅用于悟空客户端 iframe 内嵌。
- 不显示 SkillHub 顶栏、页脚、发布入口、管理后台入口。
- 展示公开技能列表、搜索框、技能卡片和安装状态。

技能卡片当前支持四种状态：

| 状态 | 页面表现 |
| --- | --- |
| 未安装 | 显示“安装”按钮 |
| 安装中 | 按钮禁用，显示“安装中...” |
| 已安装 | 显示“已安装” |
| 安装失败 | 显示“重试” |

## 4. SkillBridge 协议

悟空宿主与 iframe 通过 `postMessage` 通信。

### 4.1 消息类型

iframe 到 Host：

```ts
interface SkillBridgeRequest {
  type: 'skill-bridge-request'
  id: string
  method: string
  params: Record<string, unknown>
}
```

Host 到 iframe：

```ts
interface SkillBridgeResponse {
  type: 'skill-bridge-response'
  id: string
  bridgeVersion: string
  success: boolean
  data?: unknown
  error?: string
}
```

Host 主动推送事件：

```ts
interface SkillBridgeEvent {
  type: 'skill-bridge-event'
  event: string
  data?: unknown
}
```

协议版本当前为：

```text
1.0.0
```

### 4.2 当前使用的 Bridge API

MVP 强依赖：

| 方法 | 参数 | 用途 |
| --- | --- | --- |
| `skill.list` | 无 | 获取悟空本地已安装技能列表 |
| `skill.installFromUrl` | `{ url, name? }` | 让悟空从 URL 下载 zip 并安装技能 |

已知可后续扩展：

| 方法 | 用途 |
| --- | --- |
| `skill.removeSkillItem` | 卸载技能 |
| `skill.enableSkillItem` | 启用技能 |
| `skill.disableSkillItem` | 禁用技能 |
| `page.openUrl` | 用系统浏览器打开外链 |
| `page.getTheme` | 获取当前主题 |
| `page.getLanguage` | 获取当前语言 |
| `enterprise.getTmpAuthCode` | 获取企业免登临时授权码 |

### 4.3 Host 事件

| 事件 | data | 当前页面行为 |
| --- | --- | --- |
| `skills:ready` | `{ version: '1.0.0' }` | 标记 Bridge 就绪，调用 `skill.list` |
| `skills:changed` | `{ timestamp: number }` | 重新调用 `skill.list` 刷新安装状态 |
| `page.themeChanged` | `{ theme: 'dark' \| 'light' }` | 切换页面明暗主题 |
| `page.languageChanged` | `{ language: string }` | 切换 i18n 语言 |

注意：真实联调时遇到过 `skills:ready` 未被页面收到的情况。当前页面保留了 iframe 内兜底探测：如果页面运行在嵌入环境中，会延迟尝试一次 `skill.list`。如果调用成功，页面同样会进入“悟空已连接”状态。

## 5. 安装流程

完整安装链路：

1. 悟空配置 `skill_hub_url` 指向 SkillHub `/wukong`。
2. 用户打开悟空“能力中心 / 技能页”。
3. 悟空渲染 `ExclusiveSkillHub` iframe。
4. iframe 加载 SkillHub `/wukong` 页面。
5. 页面拉取 SkillHub 公开技能列表。
6. 页面通过 `skill.list` 获取悟空本地已安装技能。
7. 用户点击“安装”。
8. 页面生成技能 zip 下载 URL。
9. 页面调用 `skill.installFromUrl`。
10. 悟空下载 zip 并安装技能。
11. 悟空推送 `skills:changed`。
12. 页面刷新本地安装状态，按钮变为“已安装”。

安装请求示例：

```ts
await callBridge('skill.installFromUrl', {
  url: 'https://skills.zgci.org/api/v1/skills/global/skillhub/versions/1.1.7/download',
  name: 'skillhub',
})
```

## 6. 下载 URL 约定

MVP 使用已验证可用的下载接口：

```text
/api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

示例：

```text
https://skills.zgci.org/api/v1/skills/global/skillhub/versions/1.1.7/download
```

暂不使用兼容短路径：

```text
/api/v1/download/{slug}/{version}
```

该兼容路径曾出现 500 风险，不作为悟空安装主链路。

## 7. Skill Provider 的影响

Skill Provider 是后续能力，不影响当前 `/wukong` 页面打开和点击安装。

它会影响悟空运行时的技能召回和路由：

- 用户发起任务时，悟空向企业服务请求候选技能。
- 企业服务可根据用户身份、组织、权限、上下文、已安装技能等返回推荐技能。
- 如果打开“关闭本地技能召回”，悟空会依赖 Skill Provider 返回结果，不再读取用户本地技能列表。
- 如果打开“携带用户本地技能信息”，悟空请求 Provider 时会带上用户本机已安装技能，企业服务可据此过滤、排序或推荐安装。

在 Skill Provider 未实现前：

- 不填写 Skill Provider 地址。
- 不打开“关闭本地技能召回”。
- 不打开“携带用户本地技能信息”。

后续如需实现 Skill Provider，需要先拿到悟空侧完整协议，再在 SkillHub 后端新增对应接口。可能的正式地址形态类似：

```text
https://skills.zgci.org/api/wukong/skill-provider
```

该地址只是规划示例，当前不可配置。

## 8. 联调检查清单

### 8.1 页面加载

- 悟空配置页保存 `skill_hub_url`。
- 打开悟空能力中心后能看到“悟空技能中心”。
- 能看到 SkillHub 公开技能列表。
- 页面右上角显示“悟空已连接”。

如果一直显示“预览模式”或“正在检测悟空客户端连接”：

- 确认页面是通过悟空 iframe 打开的，而不是普通浏览器打开。
- 确认悟空 `ExclusiveSkillHub/useSkillBridge` 已挂载。
- 查看悟空埋点或日志：`skills_center_bridge_message_receive`、`skills_center_bridge_method_request`、`skills_center_bridge_method_success`、`skills_center_bridge_method_fail`。

### 8.2 安装

- 点击技能卡片“安装”。
- 按钮进入“安装中...”。
- 悟空收到 `skill.installFromUrl`。
- 下载 URL 返回 `200`，内容为 zip。
- 本地技能安装成功。
- 页面收到 `skills:changed` 或兜底 `skill.list` 成功刷新。
- 按钮变为“已安装”。

### 8.3 常见问题

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 页面能打开但安装按钮不可用 | Bridge 未连接，未收到 ready，或 `skill.list` 失败 | 刷新页面，查看悟空 Bridge 日志 |
| 安装失败 | 下载 URL 不可达、zip 不符合悟空技能包格式、Host 安装失败 | 直接访问下载 URL，确认 200 和 zip 内容 |
| 普通浏览器打开显示预览模式 | 正常现象，普通浏览器没有悟空 Host | 需要在悟空客户端 iframe 内测试 |
| 填了 Skill Provider 后连通性失败 | 把 `/wukong` 页面地址误填到了 Provider | Provider 先留空 |

## 9. 当前实现文件

前端页面：

```text
web/src/pages/wukong.tsx
```

Bridge 封装：

```text
web/src/shared/lib/wukong-bridge.ts
```

路由与嵌入布局：

```text
web/src/app/router.tsx
web/src/app/layout.tsx
web/src/app/layout-main-content.ts
```

i18n：

```text
web/src/i18n/locales/zh.json
web/src/i18n/locales/en.json
```

E2E 截图：

```text
web/test/screenshots/wukong/
```

## 10. 后续计划

建议按以下顺序推进：

1. 部署 `/wukong` 到测试域名，替换当前本机 IP。
2. 在悟空配置中使用测试域名联调完整安装链路。
3. 修复 `/api/v1/download/{slug}/{version}` 兼容接口 500，作为稳定性收尾。
4. 支持私有技能安装：提供短期有效的一次性下载 URL 或 signed URL。
5. 支持企业免登：通过 `enterprise.getTmpAuthCode` 换取 SkillHub 用户身份。
6. 拿到 Skill Provider 完整协议后，实现企业技能路由服务。
