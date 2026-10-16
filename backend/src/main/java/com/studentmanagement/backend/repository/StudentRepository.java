package com.studentmanagement.backend.repository;

import com.studentmanagement.backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** The student record linked to a login account. */
    Optional<Student> findByUser_Id(Long userId);

    List<Student> findAllByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
