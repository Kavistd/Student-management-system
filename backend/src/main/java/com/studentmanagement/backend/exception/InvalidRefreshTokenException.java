package com.studentmanagement.backend.exception;

/**
 * A refresh token that is unknown, expired, or revoked.
 * {@code concurrentRotation} marks the benign case where another tab rotated
 * the token moments ago; the caller must then leave the (already updated)
 * cookies alone instead of clearing them.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    private final boolean concurrentRotation;

    public InvalidRefreshTokenException(boolean concurrentRotation) {
        super("Refresh token is invalid");
        this.concurrentRotation = concurrentRotation;
    }

    public boolean isConcurrentRotation() {
        return concurrentRotation;
    }
}
