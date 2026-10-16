package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.StudentRequest;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.service.impl.StudentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTransactionTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private com.studentmanagement.backend.service.AccountDeletionService accountDeletion;

    private StudentServiceImpl studentService;

    private StudentRequest request;

    @BeforeEach
    void setUp() {
        studentService = new StudentServiceImpl(studentRepository, accountDeletion);
        request = new StudentRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane@example.com");
        request.setPhone("0700000000");
        request.setCourse("CS");
    }

    // --- @Transactional annotation presence ---

    @Test
    void manualStudentCreationIsNotExposed() {
        assertThat(java.util.Arrays.stream(StudentServiceImpl.class.getMethods()).map(Method::getName))
                .doesNotContain("createStudent");
    }

    @Test
    void updateStudent_isAnnotatedTransactional() throws NoSuchMethodException {
        Method m = StudentServiceImpl.class.getMethod("updateStudent", Long.class, StudentRequest.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    void deleteStudent_isAnnotatedTransactional() throws NoSuchMethodException {
        Method m = StudentServiceImpl.class.getMethod("deleteStudent", Long.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    void getAllStudents_isAnnotatedReadOnly() throws NoSuchMethodException {
        Method m = StudentServiceImpl.class.getMethod("getAllStudents");
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void getStudentById_isAnnotatedReadOnly() throws NoSuchMethodException {
        Method m = StudentServiceImpl.class.getMethod("getStudentById", Long.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void getStudentForUser_isAnnotatedReadOnly() throws NoSuchMethodException {
        Method m = StudentServiceImpl.class.getMethod("getStudentForUser", Long.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    // --- Rollback-triggering exceptions ---

    @Test
    void updateStudent_duplicateEmail_throwsIllegalArgument_noSave() {
        var existing = new com.studentmanagement.backend.entity.Student(); existing.setId(1L);
        var other = new com.studentmanagement.backend.entity.Student(); other.setId(2L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(studentRepository.findAllByEmailIgnoreCase(anyString())).thenReturn(java.util.List.of(other));

        assertThatThrownBy(() -> studentService.updateStudent(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateStudent_notFound_throwsResourceNotFound_noSave() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.updateStudent(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(studentRepository, never()).save(any());
    }

    @Test
    void deleteStudent_notFound_throwsResourceNotFound_noDelete() {
        doThrow(new ResourceNotFoundException("Student not found")).when(accountDeletion).deleteStudent(99L);

        assertThatThrownBy(() -> studentService.deleteStudent(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(studentRepository, never()).delete(any());
    }
}
