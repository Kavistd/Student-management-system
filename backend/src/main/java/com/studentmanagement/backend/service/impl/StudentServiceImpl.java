
package com.studentmanagement.backend.service.impl;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;
import com.studentmanagement.backend.entity.Student;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.service.StudentService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;

    @Override
    public StudentResponse createStudent(StudentRequest request) {

        Student student = new Student();

        student.setFirstName(request.getFirstName());
        student.setLastName(request.getLastName());
        student.setEmail(request.getEmail());
        student.setPhone(request.getPhone());
        student.setCourse(request.getCourse());

        Student savedStudent = studentRepository.save(student);

        return new StudentResponse(
                savedStudent.getId(),
                savedStudent.getFirstName(),
                savedStudent.getLastName(),
                savedStudent.getEmail(),
                savedStudent.getPhone(),
                savedStudent.getCourse()
        );
    }
}
