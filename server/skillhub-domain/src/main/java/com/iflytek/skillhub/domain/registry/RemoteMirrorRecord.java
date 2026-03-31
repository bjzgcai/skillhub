package com.iflytek.skillhub.domain.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(
        name = "remote_mirror_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_remote_mirror_record_skill_version", columnNames = "skill_version_id")
        }
)
public class RemoteMirrorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "source_registry", nullable = false, length = 50)
    private String sourceRegistry;

    @Column(name = "source_canonical_slug", nullable = false, length = 200)
    private String sourceCanonicalSlug;

    @Column(name = "source_namespace", nullable = false, length = 100)
    private String sourceNamespace;

    @Column(name = "source_slug", nullable = false, length = 100)
    private String sourceSlug;

    @Column(name = "requested_version", length = 64)
    private String requestedVersion;

    @Column(name = "remote_version", length = 64)
    private String remoteVersion;

    @Column(name = "bundle_sha256", length = 64)
    private String bundleSha256;

    @Column(name = "download_url", columnDefinition = "TEXT")
    private String downloadUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RemoteMirrorRecord() {
    }

    public RemoteMirrorRecord(Long skillId,
                              Long skillVersionId,
                              String sourceRegistry,
                              String sourceCanonicalSlug,
                              String sourceNamespace,
                              String sourceSlug) {
        this.skillId = skillId;
        this.skillVersionId = skillVersionId;
        this.sourceRegistry = sourceRegistry;
        this.sourceCanonicalSlug = sourceCanonicalSlug;
        this.sourceNamespace = sourceNamespace;
        this.sourceSlug = sourceSlug;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public String getSourceRegistry() {
        return sourceRegistry;
    }

    public String getSourceCanonicalSlug() {
        return sourceCanonicalSlug;
    }

    public String getSourceNamespace() {
        return sourceNamespace;
    }

    public String getSourceSlug() {
        return sourceSlug;
    }

    public String getRequestedVersion() {
        return requestedVersion;
    }

    public String getRemoteVersion() {
        return remoteVersion;
    }

    public String getBundleSha256() {
        return bundleSha256;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setRequestedVersion(String requestedVersion) {
        this.requestedVersion = requestedVersion;
    }

    public void setRemoteVersion(String remoteVersion) {
        this.remoteVersion = remoteVersion;
    }

    public void setBundleSha256(String bundleSha256) {
        this.bundleSha256 = bundleSha256;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
