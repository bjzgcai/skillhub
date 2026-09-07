CREATE TABLE skill_download_event (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    namespace_slug VARCHAR(128) NOT NULL,
    skill_slug VARCHAR(128) NOT NULL,
    version VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    user_id VARCHAR(128),
    api_token_id BIGINT,
    agent_id_hash VARCHAR(128),
    visitor_key_hash VARCHAR(128) NOT NULL,
    ip_hash VARCHAR(128),
    user_agent_hash VARCHAR(128),
    status VARCHAR(32) NOT NULL
);

CREATE INDEX idx_skill_download_event_occurred_at ON skill_download_event(occurred_at);
CREATE INDEX idx_skill_download_event_skill_source_time ON skill_download_event(skill_id, source_type, occurred_at);
CREATE INDEX idx_skill_download_event_version_time ON skill_download_event(skill_version_id, occurred_at);
CREATE INDEX idx_skill_download_event_visitor_time ON skill_download_event(visitor_key_hash, occurred_at);
