package com.iflytek.skillhub.security.secret;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GitleaksScanResponse(
        boolean passed,
        String scanner,
        @JsonProperty("scanner_version") String scannerVersion,
        List<GitleaksFinding> findings,
        boolean truncated
) {
    public List<GitleaksFinding> safeFindings() {
        return findings == null ? List.of() : findings;
    }
}
