-- Move public card badges out of the domain/operation label system.
CREATE TABLE IF NOT EXISTS skill_badge (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    badge_type VARCHAR(64) NOT NULL,
    source VARCHAR(64) NOT NULL,
    skill_version_id BIGINT NULL,
    security_audit_id BIGINT NULL,
    created_by VARCHAR(128) NULL REFERENCES user_account(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_badge_skill_type UNIQUE (skill_id, badge_type)
);

CREATE INDEX IF NOT EXISTS idx_skill_badge_skill_id ON skill_badge(skill_id);
CREATE INDEX IF NOT EXISTS idx_skill_badge_type ON skill_badge(badge_type);

-- Backfill existing allowlisted/scanner-safe data from the old label representation.
INSERT INTO skill_badge (skill_id, badge_type, source, skill_version_id, security_audit_id, created_by, created_at, updated_at)
SELECT DISTINCT sl.skill_id,
       'SCANNED_SAFE',
       'ADMIN_ALLOWLIST',
       s.latest_version_id,
       NULL::BIGINT,
       NULL::VARCHAR,
       sl.created_at,
       CURRENT_TIMESTAMP
FROM skill_label sl
JOIN label_definition ld ON ld.id = sl.label_id
JOIN skill s ON s.id = sl.skill_id
WHERE ld.slug = 'scanned-safe'
ON CONFLICT (skill_id, badge_type) DO UPDATE
    SET source = EXCLUDED.source,
        skill_version_id = EXCLUDED.skill_version_id,
        security_audit_id = EXCLUDED.security_audit_id,
        updated_at = CURRENT_TIMESTAMP;

-- Keep labels focused on domain/operation taxonomy.
DELETE FROM skill_label sl
USING label_definition ld
WHERE sl.label_id = ld.id
  AND ld.slug IN ('scanned-safe', 'requires-api-key', 'requires-oauth');

DELETE FROM label_translation lt
USING label_definition ld
WHERE lt.label_id = ld.id
  AND ld.slug IN ('scanned-safe', 'requires-api-key', 'requires-oauth');

DELETE FROM label_definition
WHERE slug IN ('scanned-safe', 'requires-api-key', 'requires-oauth');
