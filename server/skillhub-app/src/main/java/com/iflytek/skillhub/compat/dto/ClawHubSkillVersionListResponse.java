package com.iflytek.skillhub.compat.dto;

import java.util.List;

public record ClawHubSkillVersionListResponse(
        List<Item> items,
        String nextCursor
) {
    public record Item(
            String version,
            long createdAt,
            String changelog,
            String changelogSource
    ) {}
}
