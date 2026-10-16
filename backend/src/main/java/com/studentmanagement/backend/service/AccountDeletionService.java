package com.studentmanagement.backend.service;

import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.Student;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.ForbiddenOperationException;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.repository.RefreshTokenRepository;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** Explicit, atomic lifecycle shared by student deletion and direct user deletion. */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private final StudentRepository students;
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final EntityManager entityManager;

    @Transactional
    public void deleteStudent(Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        if (student.getUser() == null) {
            // No user lock exists for an unlinked legacy record. Never switch to user deletion here.
            entityManager.lock(student, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(student);
            if (student.getUser() != null) throw unexpectedRelationship();
            students.delete(student);
            students.flush();
            return;
        }
        Long userId = student.getUser().getId();
        User user = lockStudentUser(userId);
        entityManager.refresh(student);
        if (student.getUser() == null || !Objects.equals(student.getUser().getId(), userId)) {
            throw unexpectedRelationship();
        }
        List<Student> linked = students.findAllByUser_Id(userId);
        if (linked.size() != 1 || !Objects.equals(linked.getFirst().getId(), studentId)) {
            throw unexpectedRelationship();
        }
        deleteLocked(user, student);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = lockStudentUser(userId);
        List<Student> linked = students.findAllByUser_Id(userId);
        if (linked.size() > 1) throw unexpectedRelationship();
        Student student = linked.isEmpty() ? null : linked.getFirst();
        if (student != null) {
            entityManager.refresh(student);
            if (student.getUser() == null || !Objects.equals(student.getUser().getId(), userId)) {
                throw unexpectedRelationship();
            }
        }
        deleteLocked(user, student);
    }

    private User lockStudentUser(Long id) {
        // Same lock and order as refresh issuance/rotation/revocation; held through commit.
        User user = users.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        entityManager.refresh(user);
        if (user.getRole() != Role.STUDENT) throw unexpectedRelationship();
        return user;
    }

    private void deleteLocked(User user, Student student) {
        if (student != null) {
            students.delete(student);
            students.flush();
        }
        tokens.deleteAllForUser(user.getId());
        tokens.flush();
        users.delete(user);
        users.flush();
    }

    private static ForbiddenOperationException unexpectedRelationship() {
        return new ForbiddenOperationException("Account deletion refused: the student/account relationship requires administrator review.");
    }
}
