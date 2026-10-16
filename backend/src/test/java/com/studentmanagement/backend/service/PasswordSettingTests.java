package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.RegisterRequest;
import com.studentmanagement.backend.dto.request.UserRequest;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.InvalidInputException;
import com.studentmanagement.backend.provisioning.AdminPasswordResetService;
import com.studentmanagement.backend.provisioning.AdminProvisioningService;
import com.studentmanagement.backend.provisioning.ProvisioningException;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.security.JwtService;
import com.studentmanagement.backend.security.LoginRateLimiter;
import com.studentmanagement.backend.security.RefreshTokenService;
import com.studentmanagement.backend.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordSettingTests {
    static Stream<String> boundaryPasswords() {
        return Stream.of("a".repeat(71), "a".repeat(72), "a".repeat(73),
                "\u0dc3".repeat(23) + "ab", "\u0dc3".repeat(24), "\u0dc3".repeat(24) + "a",
                "\uD83D\uDE00".repeat(17) + "abc", "\uD83D\uDE00".repeat(18), "\uD83D\uDE00".repeat(18) + "a",
                "\u0dc3\uD83D\uDE00" + "a".repeat(64), "\u0dc3\uD83D\uDE00" + "a".repeat(65), "\u0dc3\uD83D\uDE00" + "a".repeat(66));
    }

    @ParameterizedTest
    @MethodSource("boundaryPasswords")
    void studentSettingPathsEnforceByteLimitBeforeEncoding(String password) {
        for (String path : List.of("register", "update")) {
            checkStudent(path, password, password.getBytes(StandardCharsets.UTF_8).length <= 72);
        }
    }

    @Test
    void studentMinimumRetainsUtf16CountingAndPasswordsAreNotTrimmed() {
        for (String path : List.of("register", "update")) {
            checkStudent(path, "a".repeat(7), false);
            checkStudent(path, "a".repeat(8), true);
            checkStudent(path, "\uD83D\uDE00".repeat(4), true);
            checkStudent(path, "  abcdef  ", true);
            checkStudent(path, "        ", false);
        }
    }

    private void checkStudent(String path, String password, boolean valid) {
        UserRepository users = mock(UserRepository.class);
        StudentRepository students = mock(StudentRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("encoded");
        User user = new User(); user.setId(1L); user.setRole(Role.STUDENT);
        when(users.lockById(1L)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(call -> { User saved = call.getArgument(0); saved.setId(1L); return saved; });
        AuthService auth = new AuthService(users, students, encoder, mock(JwtService.class),
                mock(RefreshTokenService.class), mock(LoginRateLimiter.class));
        clearInvocations(encoder);
        UserServiceImpl service = new UserServiceImpl(users, encoder, students, mock(AccountDeletionService.class));
        UserRequest request = new UserRequest();
        request.setEmail("test@example.com"); request.setUsername("test");
        request.setRole("STUDENT"); request.setPassword(password);
        Runnable operation = switch (path) {
            case "register" -> () -> auth.register(new RegisterRequest("A", "B", "test", "test@example.com", "+94 77 123 4567", password));
            default -> () -> service.updateUser(1L, request);
        };
        if (valid) {
            operation.run();
            verify(encoder).encode(password);
        } else {
            assertThatThrownBy(operation::run).isInstanceOfSatisfying(InvalidInputException.class,
                    ex -> assertThat(ex.getErrors()).containsKey("password"));
            verifyNoInteractions(encoder);
            verify(users, never()).save(any());
            verify(students, never()).save(any());
        }
    }

    @ParameterizedTest
    @MethodSource("boundaryPasswords")
    void adminSettingPathsEnforceByteLimitBeforeEncoding(String password) {
        checkAdmin(password, password.getBytes(StandardCharsets.UTF_8).length <= 72);
    }

    @Test
    void adminMinimumRetainsUtf16Counting() {
        checkAdmin("a".repeat(11), false);
        checkAdmin("a".repeat(12), true);
        checkAdmin("\uD83D\uDE00".repeat(6), true);
    }

    private void checkAdmin(String password, boolean valid) {
        for (boolean reset : List.of(false, true)) {
            UserRepository users = mock(UserRepository.class);
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            User user = new User(); user.setId(1L); user.setRole(Role.ADMIN); user.setPassword("old-hash");
            when(users.findAllByEmailIgnoreCase(anyString())).thenReturn(List.of(user));
            when(users.save(any())).thenAnswer(call -> call.getArgument(0));
            Runnable operation = reset
                    ? () -> new AdminPasswordResetService(users, encoder).resetAdminPassword("admin@example.com", password.toCharArray())
                    : () -> new AdminProvisioningService(users, encoder).provisionInitialAdmin("admin@example.com", "admin", password.toCharArray());
            if (valid) {
                operation.run();
                verify(encoder).encode(argThat(raw -> raw.toString().equals(password)));
            } else {
                assertThatThrownBy(operation::run).isInstanceOf(ProvisioningException.class);
                verifyNoInteractions(encoder);
                verify(users, never()).save(any());
                assertThat(user.getPassword()).isEqualTo("old-hash");
            }
        }
    }
}
