package com.iflytek.skillhub.dto;

import java.time.LocalDateTime;

public record SkillVersionResponse(
        Long id,
        String version,
        String status,
        String changelog,
        int fileCount,
        long totalSize,
        LocalDateTime publishedAt,
        boolean downloadAvailable
) {}
