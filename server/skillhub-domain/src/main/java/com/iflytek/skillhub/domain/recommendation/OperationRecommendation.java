package com.iflytek.skillhub.domain.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "operation_recommendation")
public class OperationRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private RecommendationSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status = RecommendationStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "cache_status", nullable = false, length = 32)
    private RecommendationCacheStatus cacheStatus = RecommendationCacheStatus.READY;

    @Column(name = "skill_id")
    private Long skillId;

    @Column(nullable = false, length = 64)
    private String namespace;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 200)
    private String reason;

    @Column(length = 64)
    private String badge;

    @Column(name = "background_image_url", length = 1000)
    private String backgroundImageUrl;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "cache_error", columnDefinition = "TEXT")
    private String cacheError;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OperationRecommendation() {
    }

    public OperationRecommendation(RecommendationSourceType sourceType, Long skillId, String namespace, String slug) {
        this.sourceType = sourceType;
        this.skillId = skillId;
        this.namespace = namespace;
        this.slug = slug;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public RecommendationSourceType getSourceType() { return sourceType; }
    public RecommendationStatus getStatus() { return status; }
    public RecommendationCacheStatus getCacheStatus() { return cacheStatus; }
    public Long getSkillId() { return skillId; }
    public String getNamespace() { return namespace; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getReason() { return reason; }
    public String getBadge() { return badge; }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public Integer getPriority() { return priority; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getCacheError() { return cacheError; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(RecommendationStatus status) { this.status = status; }
    public void setCacheStatus(RecommendationCacheStatus cacheStatus) { this.cacheStatus = cacheStatus; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setTitle(String title) { this.title = title; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setReason(String reason) { this.reason = reason; }
    public void setBadge(String badge) { this.badge = badge; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public void setCacheError(String cacheError) { this.cacheError = cacheError; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
