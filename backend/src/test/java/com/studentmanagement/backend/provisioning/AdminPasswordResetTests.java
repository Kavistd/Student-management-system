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
 * Runs against a dedicated throwaway H2 database; never touches Supabase.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:sms-reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class AdminPasswordResetTests {

    private static final char[] NEW_PASSWORD = "new-strong-passphrase-99".toCharArray();
    private static final char[] OLD_PASSWORD = "old-strong-passphrase-00".toCharArray();
    private static final String ADMIN_EMAIL = "admin@example.com";

    @Autowired
    private AdminPasswordResetService resetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        userRepository.deleteAll();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User saveUser(String email, Role role) {
        User u = new User();
        u.setUsername(email.split("@")[0]);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(new String(OLD_PASSWORD)));
        u.setRole(role);
        return userRepository.save(u);
    }

    // ── service: success ─────────────────────────────────────────────────────

    @Test
    void resetsPasswordAndVerifiesWithBcrypt() {
        User admin = saveUser(ADMIN_EMAIL, Role.ADMIN);

        Long id = resetService.resetAdminPassword(ADMIN_EMAIL, NEW_PASSWORD.clone());

        assertThat(id).isEqualTo(admin.getId());
        User updated = userRepository.findById(id).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(updated.getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(updated.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(new String(NEW_PASSWORD), updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(new String(OLD_PASSWORD), updated.getPassword())).isFalse();
    }

    @Test
    void normalizesEmailCaseBeforeLookup() {
        saveUser(ADMIN_EMAIL, Role.ADMIN);

        Long id = resetService.resetAdminPassword("  ADMIN@EXAMPLE.COM  ", NEW_PASSWORD.clone());

        assertThat(userRepository.findById(id).orElseThrow().getEmail()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    void doesNotModifyUsernameRoleOrOtherFields() {
        User admin = saveUser(ADMIN_EMAIL, Role.ADMIN);
        String originalUsername = admin.getUsername();

        resetService.resetAdminPassword(ADMIN_EMAIL, NEW_PASSWORD.clone());

        User updated = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(updated.getUsername()).isEqualTo(originalUsername);
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(updated.getEmail()).isEqualTo(ADMIN_EMAIL);
    }

    // ── service: refusals ────────────────────────────────────────────────────

    @Test
    void refusesWhenAccountDoesNotExist() {
        assertThatThrownBy(() ->
                resetService.resetAdminPassword("nobody@example.com", NEW_PASSWORD.clone()))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("No account found");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void refusesWhenAccountIsNotAdmin() {
        saveUser("student@example.com", Role.STUDENT);

        assertThatThrownBy(() ->
                resetService.resetAdminPassword("student@example.com", NEW_PASSWORD.clone()))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("not an ADMIN");

        // Password must be unchanged
        User unchanged = userRepository.findAllByEmailIgnoreCase("student@example.com").getFirst();
        assertThat(passwordEncoder.matches(new String(OLD_PASSWORD), unchanged.getPassword())).isTrue();
    }

    @Test
    void refusesPasswordTooShort() {
        saveUser(ADMIN_EMAIL, Role.ADMIN);

        assertThatThrownBy(() ->
                resetService.resetAdminPassword(ADMIN_EMAIL, "short".toCharArray()))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void refusesNullPassword() {
        saveUser(ADMIN_EMAIL, Role.ADMIN);

        assertThatThrownBy(() ->
                resetService.resetAdminPassword(ADMIN_EMAIL, null))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("at least 12");
    }

    @Test
    void refusesBlankEmail() {
        assertThatThrownBy(() ->
                resetService.resetAdminPassword("   ", NEW_PASSWORD.clone()))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("valid --admin.email");
    }

    @Test
    void refusesNullEmail() {
        assertThatThrownBy(() ->
                resetService.resetAdminPassword(null, NEW_PASSWORD.clone()))
                .isInstanceOf(ProvisioningException.class)
                .hasMessageContaining("valid --admin.email");
    }

    @Test
    void databaseIsUnchangedAfterAnyRefusal() {
        User admin = saveUser(ADMIN_EMAIL, Role.ADMIN);
        String hashBefore = userRepository.findById(admin.getId()).orElseThrow().getPassword();

        // Each of these must fail without altering the stored hash
        try { resetService.resetAdminPassword(ADMIN_EMAIL, "short".toCharArray()); } catch (ProvisioningException ignored) {}
        try { resetService.resetAdminPassword("nobody@example.com", NEW_PASSWORD.clone()); } catch (ProvisioningException ignored) {}

        String hashAfter = userRepository.findById(admin.getId()).orElseThrow().getPassword();
        assertThat(hashAfter).isEqualTo(hashBefore);
    }

    // ── runner: exit codes ───────────────────────────────────────────────────

    @Test
    void runnerReturnsZeroOnSuccess() {
        saveUser(ADMIN_EMAIL, Role.ADMIN);

        AdminPasswordResetRunner runner =
                new AdminPasswordResetRunner(resetService, () -> NEW_PASSWORD.clone(), null);

        int code = runner.execute(
                new DefaultApplicationArguments("--admin.email=" + ADMIN_EMAIL));

        assertThat(code).isZero();
        User updated = userRepository.findAllByEmailIgnoreCase(ADMIN_EMAIL).getFirst();
        assertThat(passwordEncoder.matches(new String(NEW_PASSWORD), updated.getPassword())).isTrue();
    }

    @Test
    void runnerReturnsOneWhenEmailArgumentMissing() {
        AdminPasswordResetRunner runner =
                new AdminPasswordResetRunner(resetService, () -> NEW_PASSWORD.clone(), null);

        assertThat(runner.execute(new DefaultApplicationArguments())).isEqualTo(1);
    }

    @Test
    void runnerReturnsOneWhenAccountNotFound() {
        AdminPasswordResetRunner runner =
                new AdminPasswordResetRunner(resetService, () -> NEW_PASSWORD.clone(), null);

        assertThat(runner.execute(
                new DefaultApplicationArguments("--admin.email=ghost@example.com")))
                .isEqualTo(1);
    }

    @Test
    void runnerReturnsOneWhenPasswordSourceThrows() {
        saveUser(ADMIN_EMAIL, Role.ADMIN);

        AdminPasswordResetRunner runner = new AdminPasswordResetRunner(
                resetService,
                () -> { throw new ProvisioningException("Passwords did not match; nothing was changed."); },
                null);

        assertThat(runner.execute(
                new DefaultApplicationArguments("--admin.email=" + ADMIN_EMAIL)))
                .isEqualTo(1);

        // Hash must be unchanged
        User unchanged = userRepository.findAllByEmailIgnoreCase(ADMIN_EMAIL).getFirst();
        assertThat(passwordEncoder.matches(new String(OLD_PASSWORD), unchanged.getPassword())).isTrue();
    }

    @Test
    void runnerReturnsOneForNonAdminAccount() {
        saveUser("student@example.com", Role.STUDENT);

        AdminPasswordResetRunner runner =
                new AdminPasswordResetRunner(resetService, () -> NEW_PASSWORD.clone(), null);

        assertThat(runner.execute(
                new DefaultApplicationArguments("--admin.email=student@example.com")))
                .isEqualTo(1);
    }
}
