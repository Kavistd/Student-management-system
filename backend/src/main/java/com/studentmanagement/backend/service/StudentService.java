package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.request.UpdateProfileRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;
import java.util.List;

public interface StudentService {

    List<StudentResponse> getAllStudents();

    StudentResponse getStudentById(Long id);

    StudentResponse updateStudent(Long id, StudentRequest request);

    void deleteStudent(Long id);

    /** The student record linked to the given login account. */
    StudentResponse getStudentForUser(Long userId);

    /** Updates the name and contact number on the student record linked to the given login account. */
    StudentResponse updateStudentForUser(Long userId, UpdateProfileRequest request);
}