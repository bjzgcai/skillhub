# SkillHub 全量安全扫描风险复核

时间：2026-06-04 UTC
范围：线上最新版本全量扫描中的非 PASS 项
数据来源：`/tmp/skillhub-scan-dryrun-20260604T005226Z.jsonl`

## 汇总

- latest 版本总数：50
- PASS：40，已适合展示 `扫描安全`
- WARN：4，建议展示 `需安全复核`，不展示 `扫描安全`
- FAIL：6，建议展示 `扫描未通过`，不展示 `扫描安全`
- MISSING_BUNDLE：0

## 建议标签策略

| 扫描结果 | 前端标签 | 颜色 | 处理原则 |
| --- | --- | --- | --- |
| PASS | 扫描安全 | 绿色 | 可自动展示 |
| WARN | 需安全复核 | 琥珀色 | 可展示为待复核；不阻断，但提示存在需要人工确认的风险 |
| FAIL | 扫描未通过 | 红色 | 可展示为高风险；建议修复/替换后复扫 |

说明：风险标签仍挂在 skill 维度，但只根据已发布版本的最新扫描结果同步。待审核版本的扫描结果不应影响已发布 skill 的展示标签。

## WARN 复核

### zgcai-weekly-restaurant-menu-skill@20260403.072029

- 结论：待复核 / 可能可接受
- 建议标签：`需安全复核`
- 命中：`scripts/extract_menu.py:64`，`base64-decode`，MEDIUM
- 判断：base64 解码可能是正常数据解析，也可能隐藏行为。需要看该脚本是否只处理固定来源菜单内容，是否会执行解码后的代码。

### guizang-ppt-skill@1.0.0

- 结论：待复核 / 高概率第三方库误报
- 建议标签：`需安全复核`
- 命中：`assets/motion.min.js:7`，`dynamic-code-execution`，MEDIUM
- 判断：压缩前端动画库常见动态代码特征，倾向误报；建议确认库来源与完整性，或替换为可校验来源的构建产物。

### top-venue-intel-map@1.0.0

- 结论：待复核 / 高概率第三方库误报
- 建议标签：`需安全复核`
- 命中：`assets/echarts-gl.min.js`、`assets/echarts.min.js`，动态执行与 base64 解码，MEDIUM x5
- 判断：ECharts 压缩包常见动态/编码特征，倾向误报；建议确认 assets 来源与 hash，必要时改为官方 npm 构建或记录白名单理由。

### bza-yunpan@20260601.022310

- 结论：待复核 / 需确认认证实现
- 建议标签：`需安全复核`
- 命中：`cli/bza_yunpan/auth.py:38`、`:120`，`base64-decode`，MEDIUM x2
- 判断：认证流程中使用 base64 可能正常，但涉及 auth.py，建议确认是否仅解析协议载荷/配置，不记录、不外传、不执行解码结果。

## FAIL 复核

### obsidian-sync@20260330.075529

- 结论：高风险或需明确授权边界
- 建议标签：`扫描未通过`
- 命中：`SKILL.md:56`、`:58`，`credential-file-access`，HIGH x2
- 判断：引用敏感凭据或系统文件路径。若功能确实需要读取 Obsidian/本地配置，必须明确最小权限、用途、脱敏和用户确认流程。

### knowledge-query@20260422.032033

- 结论：高风险 / 需人工确认是否为真实 secret assignment
- 建议标签：`扫描未通过`
- 命中：`SKILL.md:35`、`:42`、`:47`、`:55`，`skillhub-sensitive-assignment`，HIGH x4
- 判断：检测到高风险赋值模式，可能是真实 secret，也可能是示例变量名误报。上线展示前建议检查这些行是否包含真实密钥、token 或敏感配置值。

### skillhub@1.1.7

- 结论：高风险或需明确授权边界
- 建议标签：`扫描未通过`
- 命中：`SKILL.md:49`、`references/environment.md:46`，`credential-file-access`，HIGH x2
- 判断：引用敏感凭据/环境文件。作为运维类 skill 可能合理，但需要明确只读取必要配置、不得输出 secret，并在文档中标注安全边界。

### skill-vetter@1.0.0

- 结论：高风险但可能是安全审查类工具的预期能力
- 建议标签：`扫描未通过`，或经专项审查后降为内部可信例外
- 命中：凭据文件访问、私有记忆访问、base64、动态执行、权限提升、浏览器 cookie/session 存储引用，HIGH x4 + MEDIUM x2
- 判断：作为 vetter 类 skill，可能需要识别这些模式，但若文档要求读取这些私有文件/凭据，则必须非常谨慎。建议单独专项 review，确认它只是检测文本而不是读取用户私有资产。

### self-improvement@20260602.235733

- 结论：高风险 / 需限制范围
- 建议标签：`扫描未通过`
- 命中：`_meta.json`、`SKILL.md` 多处、`skill-card.md`，`private-agent-memory-access`，HIGH x8
- 判断：涉及 OpenClaw 私有记忆/persona 文件访问。即便功能是自我改进，也需要严格限定可读写文件、禁止外传、禁止跨会话泄露。

### notion@20260602.235739

- 结论：高风险 / 需确认凭据处理
- 建议标签：`扫描未通过`
- 命中：`_meta.json`、`SKILL.md`、`skill-card.md`，`credential-file-access`，HIGH x5
- 判断：Notion 集成常涉及 token/secret。需要确认是否只是提示用户配置环境变量，还是会读取本地敏感文件；若后者应阻断或要求明确授权。

## 建议下一步

1. 在系统标签中增加：`需安全复核`、`扫描未通过`。
2. 对 4 个 WARN 打 `需安全复核`。
3. 对 6 个 FAIL 打 `扫描未通过`。
4. 前端卡片右上角展示这些标签：绿色 `扫描安全`、琥珀色 `需安全复核`、红色 `扫描未通过`。
5. 对 `skill-vetter` 这类安全工具类 skill 单独做例外审查；如果确认能力是检测而非访问，可记录白名单理由后调整规则或标签。
