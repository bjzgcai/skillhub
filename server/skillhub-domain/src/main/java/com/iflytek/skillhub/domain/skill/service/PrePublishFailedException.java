package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

/**
 * Thrown when pre-publish security validation fails. Carries the {@link SecurityScanResponse}
 * as {@link #errorData()} so {@code GlobalExceptionHandler} can include structured
 * scan findings in the API error response.
 */
public class PrePublishFailedException extends DomainBadRequestException {

    public PrePublishFailedException(String messageCode, SecurityScanResponse securityScanResponse, Object... messageArgs) {
        super(messageCode, securityScanResponse, messageArgs);
    }
}
