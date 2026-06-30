# 本周一技：skillhub（技能助手）

## 为什么学这个

`skillhub` 是内部技能注册中心的入口技能——搜索、查看、安装、发布技能，都从它开始。掌握它，等于拿到了整个内部技能生态的钥匙：不再靠口口相传找工具，而是用一条命令精准定位你需要的能力。

## 适合谁

- 想在团队内部查找、复用已有自动化能力的开发者
- 需要发布技能到内部 registry 的技能作者
- 管理技能命名空间、版本和可见性的管理员
- 刚接触 OpenClaw / SkillHub、想知道"有哪些技能可以用"的新用户

## 3 分钟上手

### 第一步：搜索技能

想找一个能力时，先用搜索：

```bash
clawhub search "关键词" --registry https://skills.zgci.org
```

试试不同关键词——中文、英文、平台名都可以。一次搜不到不代表没有，换个词再试。

### 第二步：查看技能详情

找到候选后，看清楚再决定：

```bash
clawhub inspect <slug> --registry https://skills.zgci.org
clawhub inspect <slug> --registry https://skills.zgci.org --file SKILL.md
```

重点关注：它能做什么、需要什么权限、适用场景是什么。

### 第三步：安装技能

确认合适后，一条命令安装到 workspace：

```bash
clawhub install <slug> --registry https://skills.zgci.org
```

安装后技能出现在 workspace 的 `skills/` 目录，agent 下次启动即可使用。

### 第四步：浏览最新技能

不确定找什么？逛一逛：

```bash
clawhub explore --registry https://skills.zgci.org
```

按更新时间排列，快速了解最近有什么新能力上线。

## 10 分钟练习

### 练习 1：找到一个你可能用得上的技能

1. 想一个你日常工作中重复最多的任务（比如：周报、文档解析、数据查询）。
2. 用 2-3 组不同关键词搜索。
3. 对最相关的候选 inspect 并读 SKILL.md。
4. 判断：可直接用 / 可改造 / 不适合，并说出原因。

### 练习 2：发布一个技能

如果你有一个自建技能想分享给团队：

1. 确认 `SKILL.md` 完整（name、description 清晰）。
2. 确认已登录：`clawhub whoami --registry https://skills.zgci.org`。
3. 如果未登录，去 `https://skills.zgci.org/login` 钉钉登录，再在 Dashboard → Tokens 创建 Personal API Token，然后：
   ```bash
   clawhub login --registry https://skills.zgci.org --no-browser --token '<your-token>'
   ```
4. 发布：
   ```bash
   clawhub publish <技能目录> --registry https://skills.zgci.org --version <semver>
   ```
5. 发布后用 `clawhub inspect <slug>` 验证。

### 练习 3：理解登录与鉴权

SkillHub 的鉴权分两条路径，搞清楚区别能少踩坑：

| 场景 | 认证方式 | 未登录时的行为 |
|---|---|---|
| **Web 端**（浏览器） | 钉钉 SSO | 自动跳转登录页，登录后跳回原页面 |
| **CLI 端**（clawhub） | Personal API Token | 服务端返回 401，CLI 报错；需手动获取 token 登录 |

关键细节：
- 搜索、查看、浏览技能**不需要登录**（公开可访问）。
- 发布、删除、改权限**必须登录**。
- CLI 禁止走浏览器登录流——如果看到 `clawhub.ai/cli/auth` 提示，说明走到了公共 ClawHub，应立即停止，改用 `--registry https://skills.zgci.org --no-browser --token`。
- API Token 需要带 `skill:publish` scope 才能发布。

## 权限与风险

- 搜索和查看是只读操作，无风险。
- 安装来自外部或未知来源的技能前，**必须先用 `skill-vetter` 审查**。
- 发布操作需要 namespace 成员身份 + `skill:publish` scope 的 token。
- 不要在聊天、文档或日志中暴露 API Token 值；token 明文只在创建时显示一次。
- 发布时 `--version` 必须是合法 semver（如 `1.0.0`），且与 `SKILL.md` frontmatter 中的 `version` 保持一致，否则可能被服务端按旧版本判重。

## 验收标准

完成本周学习后，你应该能做到：

- 用 `clawhub search` 搜索技能，并能换关键词多次尝试。
- 用 `clawhub inspect` 读懂一个技能的能力边界和适用场景。
- 用 `clawhub install` 安装技能到 workspace。
- 说清楚 Web 端和 CLI 端的登录方式差异。
- 独立完成一次技能发布（含登录验证、版本号确认、发布后检查）。

## 常见问题

### 搜索搜不到想要的技能怎么办？

换关键词再试。支持中文、英文、平台名、动作名。实在找不到，可能还没有——这恰好是创建新技能的机会。

### `clawhub install` 装到哪里了？

默认装到当前 workspace 的 `skills/` 目录。可以用 `--dir` 指定其他路径。

### 发布时报 `Version already exists` 怎么办？

说明这个版本号已经发过了。解决方案：
1. 检查 `SKILL.md` 的 `version` 字段是否已更新到目标版本。
2. 递增版本号（patch 或 minor），重新发布。
3. 确保 `SKILL.md` 中的 `version` 与命令行 `--version` 完全一致。

### `clawhub whoami` 报错或显示的不是我的账号？

可能之前登录的是别人的 token，或 token 已过期/被撤销。重新创建 token 并登录即可。

### 内部 SkillHub 和公共 ClawHub 有什么区别？

内部 SkillHub（`https://skills.zgci.org`）是团队私有 registry，通过钉钉 SSO 登录。公共 ClawHub（`clawhub.ai`）是开放注册的全球 registry。用 `--registry` 参数指定目标；不要混用。

## 下一步推荐

- `skill-vetter`：安装技能前的安全审查习惯（上周一技）。
- `loop-library`：发现、审计和设计可停止、可验证的 Agent 循环工作流。
- `find-skills`：学习更高效地发现适合任务的技能。
