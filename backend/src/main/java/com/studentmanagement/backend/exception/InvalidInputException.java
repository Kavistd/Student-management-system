package com.studentmanagement.backend.exception;

import java.util.Map;

public class InvalidInputException extends IllegalArgumentException {
    private final Map<String, String> errors;

    public InvalidInputException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
