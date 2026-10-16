package com.studentmanagement.backend.exception;

/** Internal reasons distinguish retryable rotation from terminal refresh failures. */
public class InvalidRefreshTokenException extends RuntimeException {
    public enum Reason { INVALID, EXPIRED, REVOKED, REPLAYED, CONCURRENT_ROTATION }

    private final Reason reason;

    public InvalidRefreshTokenException(Reason reason) {
        super("Refresh token is invalid");
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isConcurrentRotation() {
        return reason == Reason.CONCURRENT_ROTATION;
    }
}
