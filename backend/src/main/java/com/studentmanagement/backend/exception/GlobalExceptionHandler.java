package com.studentmanagement.backend.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DuplicateRecordException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateRecordException ex) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_RECORD", ex.getMessage());
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(InvalidInputException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "VALIDATION_ERROR", "message", ex.getMessage(), "errors", ex.getErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of(
                "code", "VALIDATION_ERROR", "message",
                errors.isEmpty() ? "Some fields are invalid." : String.join(" ", errors.values()),
                "errors", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "The request body is missing or malformed.");
    }

    @ExceptionHandler(org.springframework.beans.TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(org.springframework.beans.TypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "A request parameter has an invalid value.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        // Framework exception reasons can contain implementation details.
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders())
                .body(defaultBody(ex.getStatusCode()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of("code", "RATE_LIMITED", "message", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenOperation(ForbiddenOperationException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ex instanceof CsrfException
                ? error(HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "Missing or invalid CSRF token.")
                : error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Your account does not have permission to access this resource.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
                "The request conflicts with existing data (for example, a duplicate email or a linked record).");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse frameworkError) {
            return ResponseEntity.status(frameworkError.getStatusCode()).headers(frameworkError.getHeaders())
                    .body(defaultBody(frameworkError.getStatusCode()));
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "The server could not complete the request. Please try again later.");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    private static Map<String, Object> defaultBody(HttpStatusCode status) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_INPUT";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "RESOURCE_CONFLICT";
            case 429 -> "RATE_LIMITED";
            default -> status.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "REQUEST_FAILED";
        };
        String message = status.is5xxServerError()
                ? "The server could not complete the request. Please try again later."
                : "The request could not be completed.";
        return Map.of("code", code, "message", message);
    }
}
