package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.security.SecurityVerdict;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SecurityAuditResponse(
        Long id,
        String scanId,
        String scannerType,
        SecurityVerdict verdict,
        Boolean isSafe,
        String maxSeverity,
        String riskLevel,
        String policyVersion,
        Map<String, String> scannerVersions,
        Map<String, Integer> summary,
        Integer findingsCount,
        List<SecurityFinding> findings,
        Double scanDurationSeconds,
        Instant scannedAt,
        Instant createdAt
) {
}
