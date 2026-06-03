package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RecommendationCreateRequest(
        String namespace,
        String slug,
        @Size(max = 200) String title,
        @Size(max = 2000) String summary,
        @Size(max = 200) String reason,
        @Size(max = 64) String badge,
        Integer priority,
        Instant startAt,
        Instant endAt
) {}
