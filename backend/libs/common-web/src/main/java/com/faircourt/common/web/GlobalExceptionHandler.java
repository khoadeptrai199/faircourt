package com.faircourt.common.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to the standard error body (blueprint section 13; AD-04). Clients only ever see our own messages:
 * no exception text, SQL or stack trace. Unexpected exceptions are logged with their stack trace server-side only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiException(ApiException e) {
        return respond(e.code(), e.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class
    })
    ResponseEntity<ApiError> invalidRequest(Exception e) {
        return respond(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** Fallback when a database constraint catches a conflict that no service check caught first. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrityViolation(DataIntegrityViolationException e) {
        log.warn(
                "Database constraint violation mapped to STATE_CONFLICT: {}",
                e.getClass().getSimpleName());
        return respond(ErrorCode.STATE_CONFLICT);
    }

    /** Security checks inside a service (e.g. method security) keep their meaning instead of becoming 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(AccessDeniedException e) {
        return respond(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationException e) {
        return respond(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        // Other Spring errors that carry their own status (e.g. ResponseStatusException, missing parameter,
        // unsupported media type): keep the status meaning, never turn it into a 500.
        if (e instanceof ErrorResponse errorResponse) {
            ErrorCode code = codeFor(errorResponse.getStatusCode());
            if (code != null) {
                return respond(code);
            }
        }
        log.error("Unexpected error", e);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    /** Status → code for errors raised with a status; other 4xx (406, 413, 415, …) are request-shape errors. */
    static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.STATE_CONFLICT;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_FAILED : null;
        };
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code) {
        return respond(code, code.defaultMessage());
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, message));
    }
}
