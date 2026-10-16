package com.studentmanagement.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Self-service password change. The new password is checked against PasswordPolicy in the service. */
public record ChangePasswordRequest(

    @NotBlank(message = "Current password is required")
    String currentPassword,

    @NotBlank(message = "New password is required")
    String newPassword

) {}
