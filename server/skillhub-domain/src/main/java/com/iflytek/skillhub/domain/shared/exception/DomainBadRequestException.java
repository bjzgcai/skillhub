package com.iflytek.skillhub.domain.shared.exception;

/**
 * Domain exception used when caller input violates business validation rules.
 */
public class DomainBadRequestException extends LocalizedDomainException {

    public DomainBadRequestException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    public DomainBadRequestException(String messageCode, Object errorData, Object[] messageArgs) {
        super(messageCode, errorData, messageArgs);
    }

    @Override
    public int statusCode() {
        return 400;
    }
}
