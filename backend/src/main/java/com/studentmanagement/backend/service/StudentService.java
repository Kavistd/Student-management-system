package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;

public interface StudentService {

    StudentResponse createStudent(StudentRequest request);

}