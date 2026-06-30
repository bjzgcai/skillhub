# Weekly Skills

This directory stores lightweight learning cards for the "weekly skill" program.

Operational convention:

- Use one primary skill per week.
- Keep Feishu/DingTalk pushes opt-in only; web display is the default channel.
- Before recommending external or high-permission skills, run security review or `skill-vetter`.
- Each learning card should include a quick start, practice task, risk note, and success criteria.

Recommended filename format:

```text
YYYY-Www-skill-slug.md
```

## TODO

- [ ] **getGuideContent 硬编码问题**：当前每周一技详情内容在前端 `recommendation-detail.tsx` 中硬编码路由（`if slug === 'xxx' return GUIDE`），每新增一个每周一技都要改前端代码重新发版。后续考虑将 guide 内容存入推荐记录本身（后端加 `guideContent` JSON 字段）或从 markdown 文档动态加载。
