package com.studentmanagement.backend.provisioning;

import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against its own throwaway H2 database, so no other test's data
 * (or any real database) is involved.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:sms-provisioning;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AdminProvisioningTests {

    private static final char[] STRONG_PASSWORD = "a-long-admin-passphrase".toCharArray();

    @Autowired
    private AdminProvisioningService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void emptyDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void createsOneAdminWithNormalizedEmailAndBcryptHash() {
        Long id = service.provisionInitialAdmin("  Root@Example.COM ", " root ", STRONG_PASSWORD.clone());

        User admin = userRepository.findById(id).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getEmail()).isEqualTo("root@example.com");
        assertThat(admin.getUsername()).isEqualTo("root");
        assertThat(admin.getPassword()).startsWith("$2").isNotEqualTo(new String(STRONG_PASSWORD));
        assertThat(passwordEncoder.matches(new String(STRONG_PASSWORD), admin.getPassword())).isTrue();
    }

    @Test
    void refusesWhenAnAdminAlreadyExists() {
        service.provisionInitialAdmin("first@example.com", "first", STRONG_PASSWORD.clone());

        assertThatThrownBy(() ->
                service.provisionInitialAdmin("second@example.com", "second", STRONG_PASSWORD.clone()))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("already exists");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void neverPromotesOrModifiesAnExistingAccount() {
        User student = new User();
        student.setUsername("existing");
        student.setEmail("taken@example.com");
        student.setPassword(passwordEncoder.encode("student-password"));
        student.setRole(Role.STUDENT);
        userRepository.save(student);

        assertThatThrownBy(() ->
                service.provisionInitialAdmin("TAKEN@example.com", "newadmin", STRONG_PASSWORD.clone()))
                .isInstanceOf(ProvisioningException.class);

        assertThat(userRepository.findAll())
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.getRole()).isEqualTo(Role.STUDENT);
                    assertThat(user.getUsername()).isEqualTo("existing");
                });
    }

    @Test
    void rejectsWeakPasswordsAndBadInput() {
        assertThatThrownBy(() -> service.provisionInitialAdmin("a@example.com", "a", "short".toCharArray()))
                .hasMessageContaining("at least 12");
        assertThatThrownBy(() -> service.provisionInitialAdmin("not-an-email", "a", STRONG_PASSWORD.clone()))
                .hasMessageContaining("email");
        assertThatThrownBy(() -> service.provisionInitialAdmin("a@example.com", "  ", STRONG_PASSWORD.clone()))
                .hasMessageContaining("username");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void runnerReportsExitCodesAndNeedsBothArguments() {
        AdminProvisioningRunner runner =
                new AdminProvisioningRunner(service, () -> STRONG_PASSWORD.clone(), null);

        assertThat(runner.execute(new DefaultApplicationArguments("--admin.email=ops@example.com")))
                .isEqualTo(1);
        assertThat(userRepository.count()).isZero();

        assertThat(runner.execute(new DefaultApplicationArguments(
                "--admin.email=ops@example.com", "--admin.username=ops"))).isZero();
        assertThat(runner.execute(new DefaultApplicationArguments(
                "--admin.email=ops2@example.com", "--admin.username=ops2"))).isEqualTo(1);
        assertThat(userRepository.count()).isEqualTo(1);
    }
}
