package com.studentmanagement.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

    // No format check: the email is normalized and looked up, and any mismatch
    // fails exactly like a wrong password.
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Password is too long")
    String password,

    // Optional. Persistent refresh cookie when true; browser-session cookie otherwise.
    Boolean rememberMe

) {

    public boolean persistentSession() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
