package com.studentmanagement.backend.service;

import com.studentmanagement.backend.entity.*;
import com.studentmanagement.backend.exception.ForbiddenOperationException;
import com.studentmanagement.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountDeletionRelationshipTests {
    private final StudentRepository students = mock(StudentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AccountDeletionService deletion = new AccountDeletionService(students, users, tokens, em);

    private User user() {
        User user = new User(); user.setId(1L); user.setRole(Role.STUDENT);
        when(users.lockById(1L)).thenReturn(Optional.of(user));
        return user;
    }
    private Student student(User user, long id) {
        Student student = new Student(); student.setId(id); student.setUser(user);
        return student;
    }
    private void noDeletion() {
        verify(students, never()).delete(any(Student.class));
        verify(users, never()).delete(any(User.class));
        verifyNoInteractions(tokens);
    }

    @Test
    void changedLinkAfterUserLockRefusesDeletion() {
        User user = user(), other = new User(); other.setId(9L);
        Student student = student(user, 2L);
        when(students.findById(2L)).thenReturn(Optional.of(student));
        doAnswer(call -> { student.setUser(other); return null; }).when(em).refresh(student);
        assertThatThrownBy(() -> deletion.deleteStudent(2L)).isInstanceOf(ForbiddenOperationException.class);
        verify(users).lockById(1L);
        noDeletion();
    }

    @Test
    void ambiguousStudentLinkRefusesDeletion() {
        User user = user(); Student first = student(user, 2L), second = student(user, 3L);
        when(students.findById(2L)).thenReturn(Optional.of(first));
        when(students.findAllByUser_Id(1L)).thenReturn(List.of(first, second));
        assertThatThrownBy(() -> deletion.deleteStudent(2L)).isInstanceOf(ForbiddenOperationException.class);
        noDeletion();
    }

    @Test
    void directUserDeletionRefusesAmbiguousLinks() {
        User user = user();
        when(students.findAllByUser_Id(1L)).thenReturn(List.of(student(user, 2L), student(user, 3L)));
        assertThatThrownBy(() -> deletion.deleteUser(1L)).isInstanceOf(ForbiddenOperationException.class);
        noDeletion();
    }

    @Test
    void legacyRecordBecomingLinkedDoesNotSwitchToAccountDeletion() {
        Student student = student(null, 2L);
        when(students.findById(2L)).thenReturn(Optional.of(student));
        doAnswer(call -> { student.setUser(new User()); return null; }).when(em).refresh(student);
        assertThatThrownBy(() -> deletion.deleteStudent(2L)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(users);
        noDeletion();
    }
}
