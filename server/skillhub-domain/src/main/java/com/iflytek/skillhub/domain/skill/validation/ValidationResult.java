package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;

import java.util.List;
import java.util.Optional;

public record ValidationResult(
    boolean passed,
    List<String> errors,
    boolean manualReviewRequired,
    Optional<SecurityAuditSnapshot> securityAudit
) {
    public ValidationResult(boolean passed, List<String> errors) {
        this(passed, errors, false, Optional.empty());
    }

    public static ValidationResult pass() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult pass(SecurityAuditSnapshot securityAudit) {
        return new ValidationResult(true, List.of(), false, Optional.of(securityAudit));
    }

    public static ValidationResult manualReview(SecurityAuditSnapshot securityAudit) {
        return new ValidationResult(true, List.of(), true, Optional.of(securityAudit));
    }

    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public static ValidationResult fail(String error) {
        return new ValidationResult(false, List.of(error));
    }

    public static ValidationResult fail(List<String> errors, SecurityAuditSnapshot securityAudit) {
        return new ValidationResult(false, errors, false, Optional.of(securityAudit));
    }

    public record SecurityAuditSnapshot(
            ScannerType scannerType,
            SecurityScanResponse response
    ) {
    }
}
