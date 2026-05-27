package com.iflytek.skillhub.dto;

public record PublishDisplayMetadataPreviewResponse(
        String slug,
        boolean existingSkill,
        String displayName,
        String summary
) {}
