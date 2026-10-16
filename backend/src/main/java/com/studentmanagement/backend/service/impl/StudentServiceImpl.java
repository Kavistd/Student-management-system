
package com.studentmanagement.backend.service.impl;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.dto.request.UpdateProfileRequest;
import com.studentmanagement.backend.dto.response.StudentResponse;
import com.studentmanagement.backend.entity.Student;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.exception.DuplicateRecordException;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.service.StudentService;
import com.studentmanagement.backend.service.AccountDeletionService;
import com.studentmanagement.backend.exception.InvalidInputException;
import com.studentmanagement.backend.util.ContactNumber;
import com.studentmanagement.backend.util.EmailNormalizer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final AccountDeletionService accountDeletion;

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getAllStudents() {

    return studentRepository.findAll()
            .stream()
            .map(this::mapToResponse)
            .toList();
}
    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentById(Long id) {

    Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Student not found with ID: " + id
            ));

    return mapToResponse(student);
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
    if (student.getUser() != null && !java.util.Objects.equals(
            EmailNormalizer.normalize(student.getEmail()), email)) {
        throw new InvalidInputException("email", "Linked student email is read-only until verified email changes are available.");
    }
    boolean takenByAnother = studentRepository.findAllByEmailIgnoreCase(email).stream()
            .anyMatch(existingStudent -> !existingStudent.getId().equals(id));
    if (takenByAnother) {
        throw new DuplicateRecordException("Email already exists: " + email);
    }

    // Update student information
    student.setFirstName(request.getFirstName());
    student.setLastName(request.getLastName());
    if (student.getUser() == null) student.setEmail(email);
    student.setPhone(request.getPhone());
    student.setCourse(request.getCourse());

    // Save changes to PostgreSQL
    Student updatedStudent = studentRepository.save(student);

    // Return updated student details
    return mapToResponse(updatedStudent);
}

    @Override
    @Transactional
    public void deleteStudent(Long id) {

    accountDeletion.deleteStudent(id);
}

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentForUser(Long userId) {

    // Resolved from the authenticated identity, never from a client-supplied ID
    Student student = studentRepository.findByUser_Id(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "No student record is linked to this account"
            ));

    return mapToResponse(student);
}

    @Override
    @Transactional
    public StudentResponse updateStudentForUser(Long userId, UpdateProfileRequest request) {

        // Resolved from the authenticated identity; email and course stay admin-managed
        Student student = studentRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No student record is linked to this account"
                ));

        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setPhone(ContactNumber.normalize(request.phone()));

        return mapToResponse(studentRepository.save(student));
    }

    private StudentResponse mapToResponse(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getEmail(),
                student.getPhone(),
                student.getCourse(),
                student.getUser() != null
        );
    }
}
