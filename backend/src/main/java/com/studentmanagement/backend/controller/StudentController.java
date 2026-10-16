package com.studentmanagement.backend.controller;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.request.UpdateProfileRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;
import com.studentmanagement.backend.service.StudentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public ResponseEntity<List<StudentResponse>> getAllStudents() {

    return ResponseEntity.ok(studentService.getAllStudents());
    }

    // The signed-in student's own record, identified by the JWT subject (user ID)
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> getCurrentStudent(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
                studentService.getStudentForUser(Long.valueOf(jwt.getSubject())));
    }

    // The signed-in student edits their own name and contact number
    @PutMapping("/me")
    public ResponseEntity<StudentResponse> updateCurrentStudent(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(
                studentService.updateStudentForUser(Long.valueOf(jwt.getSubject()), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getStudentById(@PathVariable Long id) {
        StudentResponse response = studentService.getStudentById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> updateStudent(
        @PathVariable Long id,
        @Valid @RequestBody StudentRequest request) {

    StudentResponse response = studentService.updateStudent(id, request);

    return ResponseEntity.ok(response);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(
        @PathVariable Long id) {

    studentService.deleteStudent(id);

    return ResponseEntity.noContent().build();
    }
}