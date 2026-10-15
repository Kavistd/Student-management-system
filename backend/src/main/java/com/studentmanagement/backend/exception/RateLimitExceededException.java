package com.studentmanagement.backend.exception;

/** Too many failed sign-ins. Maps to 429 with a Retry-After header. */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many failed sign-in attempts. Please wait before trying again.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
