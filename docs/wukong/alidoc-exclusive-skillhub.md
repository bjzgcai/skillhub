# 专属技能中心（ExclusiveSkillHub）接入指南
面向「企业自有技能中心」开发方的对客文档。
本文档对应组件：packages/skills-ui/src/components/ExclusiveSkillHub.tsx
通信桥实现：packages/skills-ui/src/hooks/useSkillBridge.ts
协议类型：packages/skills-ui/src/services/exclusive/skillBridge.types.ts

## 一、背景
「悟空」客户端的「能力中心 / 技能页」默认展示官方技能面板。当企业接入了专属技能中心后，希望在客户端内嵌入企业自建的技能管理页面，并复用宿主端的能力（安装/启用/禁用技能、跳转任务创建、企业免登等）。
为此，客户端通过 <iframe> 嵌入企业技能中心 Web 页，并基于 postMessage 提供一套 SkillBridge 协议，让 iframe 子页能调用宿主能力、并接收宿主推送的事件。

## 二、整体架构
SkillsPage (能力中心页)
  └── ExclusiveSkillHub (专属模式入口)
        ├── <iframe src={resolvedUrl}>   ← 企业技能中心 Web 页
        └── useSkillBridge               ← 双向通信桥
              ├── postMessage 监听 (iframe → Host 请求)
              ├── postMessage 推送 (Host → iframe 事件)
              └── Tauri 事件转发 (skills:changed)
                    └── CachedSkillCenterServiceAdapter
                          └── HybridSkillCenterServiceAdapter
                                ├── LocalSkillCenterServiceAdapter
                                └── RemoteSkillCenterServiceAdapter

## 三、入口与渲染流程

### 3.1 何时进入专属模式
SkillsPage 在挂载时调用 host.useExclusiveSkillHubUrl() 拉取后端下发的配置。当配置中含有 skill_hub_url 时，渲染 ExclusiveSkillHub，否则降级到默认 SkillsTab：
// packages/skills-ui/src/pages/SkillsPage.tsx
const { skillHubUrl, refresh } = host.useExclusiveSkillHubUrl();

if (skillHubUrl) {
  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-surface-base">
      <WindowDragArea />
      <div className="flex-1 min-h-0">
        <ExclusiveSkillHub skillHubUrl={skillHubUrl} />
      </div>
    </div>
  );
}
// 否则渲染默认 SkillsTab

### 3.2 iframe URL 自动注入参数
ExclusiveSkillHub 会在企业下发的 skillHubUrl 上追加三个查询参数：
参数含义取值theme当前主题dark / light（首帧快照）displayMode展示模式固定为 fullPageversion宿主 App 版本由 host.useAppVersion() 提供，可缺省
注意：theme 仅作为首帧渲染快照；后续主题变化通过 page.themeChanged 事件实时推送。

### 3.3 安全沙箱与加载体验
sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
referrerPolicy="no-referrer"
style={{ visibility: iframeLoaded ? 'visible' : 'hidden' }}
允许：脚本执行、同源访问、表单提交、弹窗
禁止：allow-top-navigation，避免 iframe 劫持主窗口跳转
iframe 初始 visibility: hidden，onLoad 触发后切换为 visible，避免白屏闪烁

## 四、SkillBridge 通信协议

### 4.1 消息结构
// iframe → Host：请求
interface SkillBridgeRequest {
  type: 'skill-bridge-request';
  id: string;                       // 唯一请求 ID（建议使用 crypto.randomUUID()）
  method: string;                   // 白名单内的方法名
  params: Record<string, unknown>;  // 方法参数
}

// Host → iframe：响应
interface SkillBridgeResponse {
  type: 'skill-bridge-response';
  id: string;                       // 与请求 ID 对应
  bridgeVersion: string;            // 桥协议构建版本
  success: boolean;
  data?: unknown;
  error?: string;
}

// Host → iframe：主动推送事件
interface SkillBridgeEvent {
  type: 'skill-bridge-event';
  event: string;
  data?: unknown;
}

### 4.2 协议版本
SKILL_BRIDGE_VERSION = '1.0.0'，skills:ready 事件中会带上该版本号。

## 五、支持的 Bridge API（白名单）
完整白名单见 SKILL_BRIDGE_ALLOWED_METHODS。非白名单方法会直接返回 success: false 错误响应。

### 5.1 技能管理类
方法参数返回说明skill.list无{ installedSkills, recommendedSkills: [] }获取已安装技能列表，并触发后台缓存刷新skill.installFromUrl{ url, name? }{ success, ...installResult }从 URL 同步安装技能skill.removeSkillItem{ success: true }卸载技能skill.enableSkillItem{ success: true }启用技能skill.disableSkillItem{ success: true }禁用技能
| skill.disable | SkillItem | { success: true } | 禁用技能 |

### 5.2 页面 / 导航类
方法参数说明page.toIndex{ skill_id?, skill_name? }跳转主页；若传了 skill_id / skill_name 且匹配到已安装技能，则跳到该技能的任务创建页page.navigateSkillCenter{ target: 'explore' | 'mine' | 'detail', skillId?, source? }在客户端内跳到技能中心的指定 Tab/详情页；target='detail' 时 skillId 必填page.openUrl{ url }用系统浏览器打开外部链接（仅支持 http(s)://）page.getTheme无返回 { theme: 'dark' | 'light' }page.getLanguage无返回 { language: string }

### 5.3 企业能力类
方法参数说明enterprise.getTmpAuthCode{ url, corpId }透传到宿主端 wukong.invoke('enterprise.getTmpAuthCode', ...)，获取企业免登临时授权码

## 六、Host 主动推送的事件
事件名触发时机dataskills:readyiframe onLoad 后调用 sendReadyEvent(){ version: '1.0.0' }skills:changed任意技能操作完成后；或 Tauri 端 skills:changed 事件触发{ timestamp: number }page.themeChanged主题模式变化（含跟随系统主题切换）{ theme: 'dark' | 'light' }page.languageChangedi18n 语言切换{ language: string }

## 七、iframe 侧接入指南（Web 端）

### 7.1 监听 Host 推送的事件
window.addEventListener('message', (event) => {
  if (event.data?.type !== 'skill-bridge-event') return;
  const { event: eventName, data } = event.data;

  switch (eventName) {
    case 'skills:ready':
      // 桥已就绪，可以开始调用 Bridge API
      console.log('Bridge ready, version:', data.version);
      break;
    case 'skills:changed':
      // 技能列表变化，刷新 UI
      refreshSkillList();
      break;
    case 'page.themeChanged':
      applyTheme(data.theme); // 'dark' | 'light'
      break;
    case 'page.languageChanged':
      applyLanguage(data.language);
      break;
  }
});

### 7.2 通用调用封装
function callBridge(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = crypto.randomUUID();

    const handler = (event) => {
      if (event.data?.type !== 'skill-bridge-response') return;
      if (event.data.id !== id) return;
      window.removeEventListener('message', handler);
      event.data.success
        ? resolve(event.data.data)
        : reject(new Error(event.data.error));
    };

    window.addEventListener('message', handler);
    window.parent.postMessage(
      { type: 'skill-bridge-request', id, method, params },
      '*', // Host 侧会用 iframe.src 的 origin 校验响应目标
    );
  });
}

### 7.3 常见调用示例
// 1) 获取已安装技能
const { installedSkills } = await callBridge('skill.list');

// 2) 从 URL 安装技能
await callBridge('skill.installFromUrl', {
  url: 'https://example.com/my-skill.zip',
  name: 'my-skill',
});

// 4) 跳转到技能中心 - 探索/我的/详情
await callBridge('page.navigateSkillCenter', { target: 'explore' });
await callBridge('page.navigateSkillCenter', { target: 'detail', skillId: 'xxx' });

// 5) 跳到任务创建页（命中已安装技能时）
await callBridge('page.toIndex', { skill_name: 'my-skill' });

// 6) 用系统浏览器打开外链
await callBridge('page.openUrl', { url: 'https://dingtalk.com' });

// 7) 企业免登
const code = await callBridge('enterprise.getTmpAuthCode', {
  url: 'https://your-enterprise-domain.com',
  corpId: 'your-corp-id',
});

## 八、安全与校验
Host 在处理每一条 postMessage 之前进行如下校验：
来源校验：event.source !== iframeRef.current?.contentWindow 直接忽略，杜绝其他窗口/插件伪造请求。
方法白名单：方法不在 SKILL_BRIDGE_ALLOWED_METHODS 内时，直接返回 success: false。
响应回传 origin：响应通过 iframe.contentWindow.postMessage(response, event.origin) 回写，不使用 '*'。
事件推送 targetOrigin：使用 new URL(iframe.src).origin 作为 targetOrigin，确保事件只发送到企业域。
因此，企业技能中心 Web 页必须部署在 skill_hub_url 实际指向的固定域名下，避免运行时跨域不一致导致事件接收不到。

## 九、企业免登（Wukong invoke 透传）
iframe 也可以直接调用 Host 暴露在 window.wukong 上的能力（前提：宿主已注入 wukong 全局）：
window.wukong.invoke('enterprise.getTmpAuthCode', {
  url: 'https://xxxxx.com',
  corpId: 'xxxxx',
});
或更推荐通过 Bridge 协议调用，避免直接耦合宿主全局：
const code = await callBridge('enterprise.getTmpAuthCode', { url, corpId });

## 十、注意事项
id 唯一性：每次 callBridge 必须使用唯一 id（推荐 crypto.randomUUID()），否则响应将被错配。
事件订阅时机：业务请等到 skills:ready 触发后再发起 Bridge 调用，避免 Host 还未挂载完毕。
缓存与刷新：skill.list 调用会在后台异步刷新缓存，连续调用是安全的；任意写操作完成后 Host 会自动推送 skills:changed 事件，iframe 侧无需手动重新拉取。
主题首帧 vs 实时：URL 上的 theme 仅是首帧值；UI 渲染请以 page.themeChanged 事件为准。
路由跳转返回：page.navigateSkillCenter 在 target='detail' 时会带 returnTo: '/skills?topTab=marketplace'，iframe 不需要关心，但能从导航 state 里读取到。

## 十一、待补充能力（Roadmap）
以下能力规划中，待管理后台与运行时支持后开放：
MCP 配置下发：管理后台支持 MCP 配置下发到企业技能中心，由 iframe 侧消费。
subAgent 配置下发：同上，支持子 Agent 的统一管理与下发。
接入过程中如遇到问题，可在排查时优先查看以下埋点：
skills_center_bridge_message_receive / skills_center_bridge_method_request / skills_center_bridge_method_success / skills_center_bridge_method_fail。
