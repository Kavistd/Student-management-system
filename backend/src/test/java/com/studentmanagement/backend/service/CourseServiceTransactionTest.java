package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.CourseRequest;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.repository.CourseRepository;
import com.studentmanagement.backend.service.impl.CourseServiceImpl;
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
class CourseServiceTransactionTest {

    @Mock
    private CourseRepository courseRepository;

    private CourseServiceImpl courseService;

    private CourseRequest request;

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository);
        request = new CourseRequest();
        request.setCourseCode("CS101");
        request.setCourseName("Computer Science");
        request.setDescription("Intro course");
        request.setDurationMonths(6);
    }

    // --- @Transactional annotation presence ---

    @Test
    void createCourse_isAnnotatedTransactional() throws NoSuchMethodException {
        Method m = CourseServiceImpl.class.getMethod("createCourse", CourseRequest.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    void updateCourse_isAnnotatedTransactional() throws NoSuchMethodException {
        Method m = CourseServiceImpl.class.getMethod("updateCourse", Long.class, CourseRequest.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    void deleteCourse_isAnnotatedTransactional() throws NoSuchMethodException {
        Method m = CourseServiceImpl.class.getMethod("deleteCourse", Long.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    void getAllCourses_isAnnotatedReadOnly() throws NoSuchMethodException {
        Method m = CourseServiceImpl.class.getMethod("getAllCourses");
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void getCourseById_isAnnotatedReadOnly() throws NoSuchMethodException {
        Method m = CourseServiceImpl.class.getMethod("getCourseById", Long.class);
        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(m.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    // --- Rollback-triggering exceptions ---

    @Test
    void createCourse_duplicateCode_throwsIllegalArgument_noSave() {
        when(courseRepository.existsByCourseCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> courseService.createCourse(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Course code already exists");

        verify(courseRepository, never()).save(any());
    }

    @Test
    void updateCourse_notFound_throwsResourceNotFound_noSave() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.updateCourse(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void deleteCourse_notFound_throwsResourceNotFound_noDelete() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deleteCourse(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(courseRepository, never()).delete(any());
    }
}
