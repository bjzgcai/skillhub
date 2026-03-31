package com.iflytek.skillhub.compat.dto;

import java.util.List;

public record ClawHubSkillVersionResponse(
        Version version,
        Skill skill
) {
    public record Version(
            String version,
            long createdAt,
            String changelog,
            String changelogSource,
            String license,
            List<FileEntry> files
    ) {}

    public record Skill(
            String slug,
            String displayName
    ) {}

    public record FileEntry(
            String path,
            long size,
            String sha256,
            String contentType
    ) {}
}
