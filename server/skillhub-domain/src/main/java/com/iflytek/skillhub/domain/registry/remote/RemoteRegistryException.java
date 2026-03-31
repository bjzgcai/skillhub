package com.iflytek.skillhub.domain.registry.remote;

/**
 * Signals failures while communicating with an external skill registry.
 */
public class RemoteRegistryException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public RemoteRegistryException(String message) {
        super(message);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public RemoteRegistryException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public RemoteRegistryException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
