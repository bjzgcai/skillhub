package com.iflytek.skillhub.security.unified;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record UnifiedSecurityScanResponse(
        @JsonProperty("scan_id") String scanId,
        String status,
        String verdict,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("scanner_versions") Map<String, String> scannerVersions,
        List<ScannerStatus> scanners,
        Map<String, Integer> summary,
        List<Finding> findings,
        @JsonProperty("duration_seconds") Double durationSeconds
) {
    List<Finding> safeFindings() {
        return findings == null ? List.of() : findings;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ScannerStatus(
            String name,
            String status,
            String version,
            @JsonProperty("duration_seconds") Double durationSeconds,
            String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Finding(
            String scanner,
            @JsonProperty("rule_id") String ruleId,
            String severity,
            String category,
            String file,
            Integer line,
            String message,
            Map<String, Object> metadata
    ) {}
}
