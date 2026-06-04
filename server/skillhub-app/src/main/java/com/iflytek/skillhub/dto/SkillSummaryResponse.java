package com.iflytek.skillhub.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SkillSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String summary,
        String status,
        Long downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String namespace,
        SkillOwnerResponse owner,
        List<SkillLabelDto> labels,
        List<SkillBadgeDto> badges,
        Instant updatedAt,
        boolean canSubmitPromotion,
        SkillLifecycleVersionResponse headlineVersion,
        SkillLifecycleVersionResponse publishedVersion,
        SkillLifecycleVersionResponse ownerPreviewVersion,
        String resolutionMode
) {
    public SkillSummaryResponse(
            Long id,
            String slug,
            String displayName,
            String summary,
            String status,
            Long downloadCount,
            Integer starCount,
            BigDecimal ratingAvg,
            Integer ratingCount,
            String namespace,
            Instant updatedAt,
            boolean canSubmitPromotion,
            SkillLifecycleVersionResponse headlineVersion,
            SkillLifecycleVersionResponse publishedVersion,
            SkillLifecycleVersionResponse ownerPreviewVersion,
            String resolutionMode) {
        this(
                id,
                slug,
                displayName,
                summary,
                status,
                downloadCount,
                starCount,
                ratingAvg,
                ratingCount,
                namespace,
                null,
                List.of(),
                List.of(),
                updatedAt,
                canSubmitPromotion,
                headlineVersion,
                publishedVersion,
                ownerPreviewVersion,
                resolutionMode
        );
    }
}
