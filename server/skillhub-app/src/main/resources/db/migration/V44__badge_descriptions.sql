ALTER TABLE skill_badge
    ADD COLUMN IF NOT EXISTS description TEXT;

UPDATE skill_badge b
SET description = CASE b.badge_type
    WHEN 'FALSE_POSITIVE_ALLOWED' THEN '扫描或关键词命中高风险描述，但当前判断为安全教育/审查说明内容触发，不代表技能自身执行该行为。'
    WHEN 'MEMORY_WRITE' THEN '该技能会引导写入 .learnings/、AGENTS.md、TOOLS.md、SOUL.md、MEMORY.md 等记忆或行为文件，适合可信个人工作区使用。'
    WHEN 'CREDENTIAL_RISK' THEN '技能文档涉及 API Key / token 的本地保存或读取，建议使用环境变量或平台 secret 管理，避免明文落盘。'
    WHEN 'LOCAL_FILE_SYNC' THEN '该技能涉及本地同步服务、访问 token、工作区/笔记目录读写，应确认同步范围和授权配置。'
    WHEN 'PENDING_REVIEW' THEN '当前技能缺少可用安全扫描背书，或版本仍处于草稿/待审核状态，不建议作为已扫描安全技能展示。'
    ELSE b.description
END,
updated_at = CURRENT_TIMESTAMP
WHERE b.badge_type IN (
    'FALSE_POSITIVE_ALLOWED',
    'MEMORY_WRITE',
    'CREDENTIAL_RISK',
    'LOCAL_FILE_SYNC',
    'PENDING_REVIEW'
);
