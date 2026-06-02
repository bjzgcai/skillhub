package com.iflytek.skillhub.security.secret;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitleaksFinding(
        @JsonProperty("rule_id") String ruleId,
        String description,
        String file,
        @JsonProperty("start_line") Integer startLine,
        @JsonProperty("end_line") Integer endLine,
        @JsonProperty("start_column") Integer startColumn,
        @JsonProperty("end_column") Integer endColumn,
        Double entropy,
        String fingerprint,
        @JsonProperty("redacted_secret") String redactedSecret
) {
}
