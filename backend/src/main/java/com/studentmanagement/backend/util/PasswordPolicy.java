package com.studentmanagement.backend.util;

import com.studentmanagement.backend.exception.InvalidInputException;
import java.nio.charset.StandardCharsets;

/** Validates new student passwords before BCrypt; length uses existing UTF-16 counting. */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validateStudentPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidInputException("password", "Password is required");
        }
        if (password.length() < 8) {
            throw new InvalidInputException("password", "Password must be at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidInputException("password", "Password must be at most 72 UTF-8 bytes");
        }
    }
}
