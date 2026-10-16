package com.studentmanagement.backend.exception;

/** A known uniqueness conflict, separate from invalid input or programming errors. */
public class DuplicateRecordException extends IllegalArgumentException {
    public DuplicateRecordException(String message) {
        super(message);
    }
}
