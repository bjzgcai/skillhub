-- Recommendation records are only for locally cached, downloadable skills.
-- External URLs are handled by ops/recommend-external-skill.sh before a recommendation is created.
DELETE FROM operation_recommendation
WHERE skill_id IS NULL;

DROP INDEX IF EXISTS ux_operation_recommendation_source_url;
DROP INDEX IF EXISTS ux_operation_recommendation_active_skill;

ALTER TABLE operation_recommendation
    DROP COLUMN IF EXISTS source_url,
    ALTER COLUMN skill_id SET NOT NULL;

ALTER TABLE operation_recommendation
    ADD CONSTRAINT fk_operation_recommendation_skill
    FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX ux_operation_recommendation_skill_non_deleted
    ON operation_recommendation (skill_id)
    WHERE status <> 'DELETED';
