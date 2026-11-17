package com.faircourt.common.web;

/**
 * The only exception code throws for an expected failure (AD-04). Its message is sent to the client, so it must be
 * safe text written by us.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
