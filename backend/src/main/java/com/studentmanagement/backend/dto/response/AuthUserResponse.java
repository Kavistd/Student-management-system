package com.studentmanagement.backend.dto.response;

/** The signed-in user. Never contains tokens or password hashes. */
public record AuthUserResponse(
    Long id,
    String username,
    String email,
    String role
) {}
