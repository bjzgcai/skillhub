package com.iflytek.skillhub.domain.shared.exception;

/**
 * Base class for domain-layer exceptions that carry a localized message code and arguments.
 */
public abstract class LocalizedDomainException extends RuntimeException implements LocalizedMessage {

    private final String messageCode;
    private final Object[] messageArgs;
    /**
     * Structured data (e.g. SecurityScanResponse) carried alongside the exception
     * for inclusion in API error responses. Marked transient to exclude from
     * Java serialization (e.g. Spring Session persistence) since domain
     * objects may not be serializable.
     */
    private final transient Object errorData;

    protected LocalizedDomainException(String messageCode, Object... messageArgs) {
        super(messageCode);
        this.messageCode = messageCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
        this.errorData = null;
    }

    protected LocalizedDomainException(String messageCode, Object errorData, Object[] messageArgs) {
        super(messageCode);
        this.messageCode = messageCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs;
        this.errorData = errorData;
    }

    public String messageCode() {
        return messageCode;
    }

    public Object[] messageArgs() {
        return messageArgs.clone();
    }

    public Object errorData() {
        return errorData;
    }

    public abstract int statusCode();
}
