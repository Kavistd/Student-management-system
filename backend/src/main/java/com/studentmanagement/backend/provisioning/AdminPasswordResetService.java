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
import java.util.List;

/**
 * Resets the password of an existing ADMIN account.
 *
 * <p>Only the {@code password} column is touched. Email, username, role and
 * every other field are left exactly as they are. The operation is refused
 * when the account is missing, belongs to a non-ADMIN role, or the supplied
 * password does not satisfy the existing policy.
 */
@Service
public class AdminPasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordResetService(UserRepository userRepository,
                                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Resets the password for the ADMIN identified by {@code rawEmail}.
     *
     * @return the user id of the updated account
     * @throws ProvisioningException if any precondition is not met
     */
    @Transactional
    public Long resetAdminPassword(String rawEmail, char[] newPassword) {

        String email = EmailNormalizer.normalize(rawEmail);

        if (email == null || email.isBlank()) {
            throw new ProvisioningException("A valid --admin.email is required.");
        }

        validatePassword(newPassword);

        List<User> matches = userRepository.findAllByEmailIgnoreCase(email);

        if (matches.isEmpty()) {
            throw new ProvisioningException(
                    "No account found for that email address; nothing was changed.");
        }
        if (matches.size() > 1) {
            throw new ProvisioningException(
                    "More than one account shares that email address; nothing was changed.");
        }

        User user = matches.getFirst();

        if (user.getRole() != Role.ADMIN) {
            throw new ProvisioningException(
                    "That account exists but is not an ADMIN; nothing was changed.");
        }

        user.setPassword(passwordEncoder.encode(CharBuffer.wrap(newPassword)));
        return userRepository.save(user).getId();
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < AdminProvisioningService.MIN_PASSWORD_LENGTH) {
            throw new ProvisioningException(
                    "The password must be at least "
                            + AdminProvisioningService.MIN_PASSWORD_LENGTH + " characters.");
        }
        if (StandardCharsets.UTF_8.encode(CharBuffer.wrap(password)).remaining()
                > AdminProvisioningService.MAX_PASSWORD_BYTES) {
            throw new ProvisioningException(
                    "The password must be at most "
                            + AdminProvisioningService.MAX_PASSWORD_BYTES + " bytes.");
        }
    }
}
