package com.iflytek.skillhub.domain.skill.metadata;

import java.util.Map;

public record SkillMetadata(
    String name,
    String description,
    String version,
    String body,
    Map<String, Object> frontmatter,
    String displayName,
    String summary
) {
    public SkillMetadata(
        String name,
        String description,
        String version,
        String body,
        Map<String, Object> frontmatter
    ) {
        this(name, description, version, body, frontmatter, null, null);
    }
}
