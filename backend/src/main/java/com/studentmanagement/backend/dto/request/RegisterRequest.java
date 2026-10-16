
package com.studentmanagement.backend.dto.request;

import com.studentmanagement.backend.util.ContactNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Username is required")
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    String email,

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = ContactNumber.PATTERN, message = ContactNumber.MESSAGE)
    String phone,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password

) {}
