package com.iflytek.skillhub.dto;

import java.time.Instant;

public record RecommendationResponse(
        String sourceType,
        String status,
        String cacheStatus,
        Long skillId,
        String namespace,
        String slug,
        String title,
        String summary,
        String reason,
        String badge,
        String backgroundImageUrl,
        Integer priority,
        Instant startAt,
        Instant endAt,
        String cacheError,
        SkillSummaryResponse skill,
        Instant createdAt,
        Instant updatedAt
) {}
