package com.faircourt.common.web;

/**
 * The only exception code throws for an expected failure (AD-04). Its message is sent to the client, so it must be
 * safe text written by us.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Integer retryAfterSeconds;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String safeMessage) {
        this(code, safeMessage, null);
    }

    /** For throttled calls: the answer also carries a {@code Retry-After} header. */
    public ApiException(ErrorCode code, String safeMessage, Integer retryAfterSeconds) {
        super(safeMessage);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    /** Seconds the caller should wait, or null when the answer has no Retry-After. */
    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
