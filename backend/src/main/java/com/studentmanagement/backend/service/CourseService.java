package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.CourseRequest;
import com.studentmanagement.backend.dto.response.CourseResponse;

import java.util.List;

public interface CourseService {

    CourseResponse createCourse(CourseRequest request);

    List<CourseResponse> getAllCourses();

    CourseResponse getCourseById(Long id);

    CourseResponse updateCourse(Long id, CourseRequest request);

    void deleteCourse(Long id);
}