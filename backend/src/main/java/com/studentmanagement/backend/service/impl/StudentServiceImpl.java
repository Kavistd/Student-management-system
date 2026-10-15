
package com.studentmanagement.backend.service.impl;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;
import com.studentmanagement.backend.entity.Student;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.service.StudentService;
import com.studentmanagement.backend.util.EmailNormalizer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;

    @Override
    @Transactional
    public StudentResponse createStudent(StudentRequest request) {

        String email = EmailNormalizer.normalize(request.getEmail());
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        Student student = new Student();

        student.setFirstName(request.getFirstName());
        student.setLastName(request.getLastName());
        student.setEmail(email);
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
    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getAllStudents() {

    return studentRepository.findAll()
            .stream()
            .map(student -> new StudentResponse(
                    student.getId(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getEmail(),
                    student.getPhone(),
                    student.getCourse()
            ))
            .toList();
}
    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentById(Long id) {

    Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Student not found with ID: " + id
            ));

    return new StudentResponse(
            student.getId(),
            student.getFirstName(),
            student.getLastName(),
            student.getEmail(),
            student.getPhone(),
            student.getCourse()
    );
}
    @Override
    @Transactional
    public StudentResponse updateStudent(Long id, StudentRequest request) {

    // Find the existing student
    Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Student not found with ID: " + id
            ));

    // Check whether the new email belongs to another student
    String email = EmailNormalizer.normalize(request.getEmail());
    boolean takenByAnother = studentRepository.findAllByEmailIgnoreCase(email).stream()
            .anyMatch(existingStudent -> !existingStudent.getId().equals(id));
    if (takenByAnother) {
        throw new IllegalArgumentException("Email already exists: " + email);
    }

    // Update student information
    student.setFirstName(request.getFirstName());
    student.setLastName(request.getLastName());
    student.setEmail(email);
    student.setPhone(request.getPhone());
    student.setCourse(request.getCourse());

    // Save changes to PostgreSQL
    Student updatedStudent = studentRepository.save(student);

    // Return updated student details
    return new StudentResponse(
            updatedStudent.getId(),
            updatedStudent.getFirstName(),
            updatedStudent.getLastName(),
            updatedStudent.getEmail(),
            updatedStudent.getPhone(),
            updatedStudent.getCourse()
    );
}

    @Override
    @Transactional
    public void deleteStudent(Long id) {

    // Find the student by ID
    Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Student not found with ID: " + id
            ));

    // Delete the student from database
    studentRepository.delete(student);
}

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentForUser(Long userId) {

    // Resolved from the authenticated identity, never from a client-supplied ID
    Student student = studentRepository.findByUser_Id(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "No student record is linked to this account"
            ));

    return new StudentResponse(
            student.getId(),
            student.getFirstName(),
            student.getLastName(),
            student.getEmail(),
            student.getPhone(),
            student.getCourse()
    );
}
}
