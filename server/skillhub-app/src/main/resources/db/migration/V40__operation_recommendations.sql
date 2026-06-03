CREATE TABLE operation_recommendation (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    cache_status VARCHAR(32) NOT NULL,
    skill_id BIGINT,
    namespace VARCHAR(64) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    source_url TEXT,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    reason VARCHAR(200),
    badge VARCHAR(64),
    priority INTEGER NOT NULL DEFAULT 0,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    cache_error TEXT,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_operation_recommendation_display
    ON operation_recommendation (status, cache_status, priority DESC, updated_at DESC);

CREATE INDEX idx_operation_recommendation_skill
    ON operation_recommendation (skill_id);

CREATE UNIQUE INDEX ux_operation_recommendation_active_skill
    ON operation_recommendation (skill_id)
    WHERE status = 'ACTIVE' AND skill_id IS NOT NULL;

CREATE UNIQUE INDEX ux_operation_recommendation_source_url
    ON operation_recommendation (source_url)
    WHERE status <> 'DELETED' AND source_url IS NOT NULL;
