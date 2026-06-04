package com.iflytek.skillhub.domain.badge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_badge")
public class SkillBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "badge_type", nullable = false, length = 64)
    private String badgeType;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Column(name = "security_audit_id")
    private Long securityAuditId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillBadge() {
    }

    public SkillBadge(Long skillId, String badgeType, String source, Long skillVersionId, Long securityAuditId, String createdBy) {
        this.skillId = skillId;
        this.badgeType = badgeType;
        this.source = source;
        this.skillVersionId = skillVersionId;
        this.securityAuditId = securityAuditId;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getSkillId() { return skillId; }
    public String getBadgeType() { return badgeType; }
    public String getSource() { return source; }
    public Long getSkillVersionId() { return skillVersionId; }
    public Long getSecurityAuditId() { return securityAuditId; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }

    public void refresh(String source, Long skillVersionId, Long securityAuditId, String createdBy) {
        this.source = source;
        this.skillVersionId = skillVersionId;
        this.securityAuditId = securityAuditId;
        this.createdBy = createdBy;
    }

    public void updateDescription(String description) {
        this.description = description;
    }
}
