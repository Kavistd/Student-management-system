package com.studentmanagement.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateRecord_duplicateEmail_returnsConflictWithJsonBody() {
        ResponseEntity<?> response =
                handler.handleDuplicate(new DuplicateRecordException("Email already exists: test@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("DUPLICATE_RECORD");
        assertThat(body.get("message")).isEqualTo("Email already exists: test@example.com");
    }

    @Test
    void duplicateRecord_duplicateCourseCode_returnsConflictWithJsonBody() {
        ResponseEntity<?> response =
                handler.handleDuplicate(new DuplicateRecordException("Course code already exists: CS101"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("DUPLICATE_RECORD");
        assertThat(body.get("message")).isEqualTo("Course code already exists: CS101");
    }

    @Test
    void resourceNotFound_returnsNotFoundWithJsonBody() {
        ResponseEntity<?> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Student not found with ID: 99"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "code", "RESOURCE_NOT_FOUND", "message", "Student not found with ID: 99"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalDetails() {
        var response = handler.handleUnexpected(new IllegalStateException("SQL password=secret"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().toString()).doesNotContain("SQL", "secret", "IllegalStateException");
    }

    @Test
    void arbitraryIllegalArgumentIsNotTreatedAsDuplicateOrExposed() {
        var response = handler.handleUnexpected(new IllegalArgumentException("internal detail"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_SERVER_ERROR");
    }

    @Test
    void invalidInputIncludesFieldErrors() {
        var response = handler.handleInvalidInput(new InvalidInputException("role", "Invalid role"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR")
                .containsEntry("errors", Map.of("role", "Invalid role"));
    }

    @Test
    void rateLimitPreservesRetryAfter() {
        var response = handler.handleRateLimit(new RateLimitExceededException(45));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("45");
        assertThat(response.getBody()).containsEntry("code", "RATE_LIMITED");
    }

    @Test
    void integrityErrorsDoNotExposeSql() {
        var response = handler.handleDataIntegrity(new org.springframework.dao.DataIntegrityViolationException("secret SQL"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "RESOURCE_CONFLICT");
        assertThat(response.getBody().toString()).doesNotContain("secret", "SQL");
    }

    @Test
    void frameworkErrorsRetainStatusAndHeadersWithoutExceptionReason() {
        var response = handler.handleUnexpected(new org.springframework.web.HttpRequestMethodNotSupportedException("PATCH", java.util.List.of("GET")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("GET");
        var failure = handler.handleResponseStatus(new org.springframework.web.server.ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "secret SQL"));
        assertThat(failure.getBody().toString()).doesNotContain("secret", "SQL");
    }

    @org.springframework.web.bind.annotation.RestController
    static class FailureController {
        @org.springframework.web.bind.annotation.GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("SQL internal-password");
        }
    }

    @Test
    void unexpectedControllerFailureReturnsSafeJson500() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new FailureController()).setControllerAdvice(handler).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/unexpected"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("The server could not complete the request. Please try again later."));
    }
}
