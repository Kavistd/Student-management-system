package com.studentmanagement.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgument_duplicateEmail_returnsConflictWithJsonBody() {
        ResponseEntity<?> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Email already exists: test@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("DUPLICATE_RECORD");
        assertThat(body.get("message")).isEqualTo("Email already exists: test@example.com");
    }

    @Test
    void illegalArgument_duplicateCourseCode_returnsConflictWithJsonBody() {
        ResponseEntity<?> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Course code already exists: CS101"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("DUPLICATE_RECORD");
        assertThat(body.get("message")).isEqualTo("Course code already exists: CS101");
    }

    @Test
    void resourceNotFound_returnsNotFoundWithPlainMessage() {
        ResponseEntity<?> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Student not found with ID: 99"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("Student not found with ID: 99");
    }
}
