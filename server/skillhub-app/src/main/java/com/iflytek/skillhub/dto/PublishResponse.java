package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.security.SecurityFinding;
import com.iflytek.skillhub.domain.security.SecurityVerdict;

import java.util.List;

public record PublishResponse(
        Long skillId,
        String namespace,
        String slug,
        String version,
        String status,
        int fileCount,
        long totalSize,
        PublishSecurityAudit securityAudit
) {
    public record PublishSecurityAudit(
            String scanId,
            SecurityVerdict verdict,
            int findingsCount,
            String maxSeverity,
            List<SecurityFinding> findings
    ) {
    }
}
