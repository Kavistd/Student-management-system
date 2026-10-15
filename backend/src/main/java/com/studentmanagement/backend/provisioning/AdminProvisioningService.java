package com.studentmanagement.backend.provisioning;

import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.util.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Creates the first ADMIN account. Refuses, without changing anything, when
 * any ADMIN already exists or the email/username is taken. Existing accounts
 * are never promoted or modified.
 */
@Service
public class AdminProvisioningService {

    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MAX_PASSWORD_BYTES = 72; // BCrypt input limit

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminProvisioningService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long provisionInitialAdmin(String rawEmail, String rawUsername, char[] password) {

        String email = EmailNormalizer.normalize(rawEmail);
        String username = rawUsername == null ? "" : rawUsername.trim();

        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new ProvisioningException("A valid --admin.email is required.");
        }
        if (username.isEmpty()) {
            throw new ProvisioningException("A non-blank --admin.username is required.");
        }
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new ProvisioningException(
                    "The password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).remaining() > MAX_PASSWORD_BYTES) {
            throw new ProvisioningException(
                    "The password must be at most " + MAX_PASSWORD_BYTES + " bytes.");
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            throw new ProvisioningException(
                    "An ADMIN account already exists. Initial provisioning runs only once; nothing was changed.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ProvisioningException(
                    "That email already belongs to an account. Existing accounts are never modified; nothing was changed.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ProvisioningException(
                    "That username is already taken; nothing was changed.");
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(CharBuffer.wrap(password)));
        admin.setRole(Role.ADMIN);

        return userRepository.save(admin).getId();
    }
}
