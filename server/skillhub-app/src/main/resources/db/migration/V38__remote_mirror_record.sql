CREATE TABLE remote_mirror_record (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    source_registry VARCHAR(50) NOT NULL,
    source_canonical_slug VARCHAR(200) NOT NULL,
    source_namespace VARCHAR(100) NOT NULL,
    source_slug VARCHAR(100) NOT NULL,
    requested_version VARCHAR(64),
    remote_version VARCHAR(64),
    bundle_sha256 VARCHAR(64),
    download_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_remote_mirror_record_skill_version UNIQUE (skill_version_id)
);

CREATE INDEX idx_remote_mirror_record_source
    ON remote_mirror_record (source_registry, source_canonical_slug);

CREATE INDEX idx_remote_mirror_record_skill
    ON remote_mirror_record (skill_id);
