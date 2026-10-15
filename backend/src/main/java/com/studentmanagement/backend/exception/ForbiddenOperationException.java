package com.studentmanagement.backend.exception;

/** An authenticated request the API refuses by policy (e.g. role escalation). Maps to 403. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
