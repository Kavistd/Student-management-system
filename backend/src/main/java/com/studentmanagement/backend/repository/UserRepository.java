
package com.studentmanagement.backend.repository;

import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Acquire before reading or modifying refresh-token state; held until transaction completion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") Long id);


    /** Emails are compared case-insensitively so older mixed-case rows still match. */
    List<User> findAllByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);
}
