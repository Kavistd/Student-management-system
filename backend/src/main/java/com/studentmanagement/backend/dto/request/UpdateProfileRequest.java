package com.studentmanagement.backend.dto.request;

import com.studentmanagement.backend.util.ContactNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The contact details a student may change on their own record. Email and course are not editable. */
public record UpdateProfileRequest(

    @NotBlank(message = "First name is required")
    @Size(max = 255, message = "First name must be at most 255 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(max = 255, message = "Last name must be at most 255 characters")
    String lastName,

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = ContactNumber.PATTERN, message = ContactNumber.MESSAGE)
    String phone

) {}
