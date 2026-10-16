package com.studentmanagement.backend.service;

import com.studentmanagement.backend.entity.*;
import com.studentmanagement.backend.repository.*;
import com.studentmanagement.backend.security.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sms-deletion-rollback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class AccountDeletionRollbackTests {
    @Autowired AccountDeletionService deletion;
    @Autowired StudentRepository students;
    @MockitoSpyBean UserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired RefreshTokenService refresh;

    @Test
    void failureAfterFlushedChildDeletesRestoresStudentUserAndTokens() {
        User user = new User(); user.setUsername(UUID.randomUUID().toString());
        user.setEmail(user.getUsername() + "@example.com"); user.setPassword("test-hash"); user.setRole(Role.STUDENT);
        users.save(user);
        Student student = new Student(); student.setFirstName("Rollback"); student.setLastName("Test");
        student.setEmail(user.getEmail()); student.setUser(user); students.save(student);
        refresh.issue(user, true);
        long tokensBefore = tokens.count();
        doThrow(new IllegalStateException("Simulated final deletion failure")).when(users).delete(any(User.class));
        assertThatThrownBy(() -> deletion.deleteStudent(student.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(user.getId())).isPresent();
        assertThat(students.findById(student.getId())).isPresent();
        assertThat(tokens.count()).isEqualTo(tokensBefore);
        assertThat(tokens.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId()))
                .allMatch(t -> t.getRevokedAt() == null)).isTrue();
    }
}
