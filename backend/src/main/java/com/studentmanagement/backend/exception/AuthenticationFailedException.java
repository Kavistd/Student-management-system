package com.studentmanagement.backend.exception;

/** Wrong email or password, deliberately without saying which. Maps to 401. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
