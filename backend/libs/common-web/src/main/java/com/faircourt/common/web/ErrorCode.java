package com.faircourt.common.web;

import org.springframework.http.HttpStatus;

/**
 * Every error code an API response can carry (blueprint section 13; AD-04). The same codes are used by the gateway
 * and every service. Messages are safe, generic text; never put exception text, SQL or personal data in them.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request is invalid."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Sign in or check in to continue."),
    GUEST_ACCESS_EXPIRED(HttpStatus.UNAUTHORIZED, "Your guest access has expired."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "The security token is missing or invalid. Reload and try again."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission for this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This method is not allowed here."),
    // Message from blueprint section 13.
    MATCH_CONFLICT(HttpStatus.CONFLICT, "A player or court is no longer available."),
    STATE_CONFLICT(HttpStatus.CONFLICT, "This changed in the meantime. Refresh and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Try again."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A service is not available right now. Try again.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
