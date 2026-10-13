package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;
import java.util.List;

public interface StudentService {

    StudentResponse createStudent(StudentRequest request);

    List<StudentResponse> getAllStudents();

    StudentResponse getStudentById(Long id);

    StudentResponse updateStudent(Long id, StudentRequest request);

    void deleteStudent(Long id);
}