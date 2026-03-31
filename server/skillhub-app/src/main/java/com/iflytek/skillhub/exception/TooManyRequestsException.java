package com.iflytek.skillhub.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-layer exception mapped to HTTP 429 with a localized error code.
 */
public class TooManyRequestsException extends LocalizedException {

    public TooManyRequestsException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
