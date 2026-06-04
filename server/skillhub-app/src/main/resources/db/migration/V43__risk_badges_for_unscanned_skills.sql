-- Mark active skills that are intentionally not labelled as scanned safe with concise risk badges.
-- These badges are manual product risk indicators, not scanner verdicts.
WITH target_badges(skill_slug, badge_type) AS (
    VALUES
        ('skill-vetter', 'FALSE_POSITIVE_ALLOWED'),
        ('self-improvement', 'MEMORY_WRITE'),
        ('notion', 'CREDENTIAL_RISK'),
        ('obsidian-sync', 'LOCAL_FILE_SYNC'),
        ('wild-idea', 'PENDING_REVIEW'),
        ('wildidea', 'PENDING_REVIEW')
), latest_versions AS (
    SELECT s.id AS skill_id,
           s.slug,
           v.id AS skill_version_id,
           row_number() OVER (
               PARTITION BY s.id
               ORDER BY CASE WHEN v.status = 'PUBLISHED' THEN 0 ELSE 1 END,
                        v.created_at DESC,
                        v.id DESC
           ) AS rn
    FROM skill s
    JOIN skill_version v ON v.skill_id = s.id
    JOIN target_badges tb ON tb.skill_slug = s.slug
    WHERE s.status = 'ACTIVE'
      AND s.hidden = FALSE
)
INSERT INTO skill_badge (skill_id, badge_type, source, skill_version_id, security_audit_id, created_by, created_at, updated_at)
SELECT lv.skill_id,
       tb.badge_type,
       'MANUAL_RISK_REVIEW',
       lv.skill_version_id,
       NULL::BIGINT,
       NULL::VARCHAR,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM target_badges tb
JOIN latest_versions lv ON lv.slug = tb.skill_slug AND lv.rn = 1
ON CONFLICT (skill_id, badge_type) DO UPDATE
    SET source = EXCLUDED.source,
        skill_version_id = EXCLUDED.skill_version_id,
        security_audit_id = EXCLUDED.security_audit_id,
        updated_at = CURRENT_TIMESTAMP;
