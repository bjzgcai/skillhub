package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-layer exception mapped to HTTP 503 with a localized error code.
 */
public class ServiceUnavailableException extends LocalizedException {

    public ServiceUnavailableException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
