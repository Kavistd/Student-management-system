
package com.studentmanagement.backend.service.impl;

import com.studentmanagement.backend.dto.request.UserRequest;
import com.studentmanagement.backend.dto.response.UserResponse;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.ForbiddenOperationException;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.exception.DuplicateRecordException;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.service.UserService;
import com.studentmanagement.backend.service.AccountDeletionService;
import com.studentmanagement.backend.repository.StudentRepository;
import org.springframework.transaction.annotation.Transactional;
import com.studentmanagement.backend.util.EmailNormalizer;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import com.studentmanagement.backend.exception.InvalidInputException;
import com.studentmanagement.backend.util.PasswordPolicy;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;
    private final AccountDeletionService accountDeletion;

    @Override
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public UserResponse getUserById(Long id) {

        User user = findUser(id);
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {

        User user = userRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        requireNotAdminAccount(user);
        Role role = requireNonAdminRole(request.getRole());
        PasswordPolicy.validateStudentPassword(request.getPassword());
        String email = EmailNormalizer.normalize(request.getEmail());
        boolean linked = studentRepository.existsByUser_Id(id);
        if (linked && !java.util.Objects.equals(EmailNormalizer.normalize(user.getEmail()), email)) {
            throw new InvalidInputException("email", "Linked account email is read-only until verified email changes are available.");
        }

        boolean takenByAnother = userRepository.findAllByEmailIgnoreCase(email).stream()
                .anyMatch(existingUser -> !existingUser.getId().equals(id));
        if (takenByAnother) {
            throw new DuplicateRecordException(
                    "Email already exists: " + email
            );
        }

        user.setUsername(request.getUsername().trim());
        if (!linked) user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        return mapToResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long id) {

        accountDeletion.deleteUser(id);
    }

    /**
     * ADMIN accounts are only created by the one-time provisioning command.
     * The API cannot grant the ADMIN role.
     */
    private Role requireNonAdminRole(String requestedRole) {

        Role role = parseRole(requestedRole);
        if (role == Role.ADMIN) {
            throw new ForbiddenOperationException(
                    "Creating ADMIN accounts or granting the ADMIN role through the API is disabled."
            );
        }
        return role;
    }

    /** Prevents changing, demoting, or deleting administrators (and admin lock-out) via the API. */
    private void requireNotAdminAccount(User user) {

        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenOperationException(
                    "ADMIN accounts cannot be modified or deleted through the API."
            );
        }
    }

    private User findUser(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + id
                ));
    }

    private Role parseRole(String role) {

        try {
            return Role.valueOf(
                    role.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidInputException(
                    "role", "Invalid role. Allowed values: ADMIN, STUDENT"
            );
        }
    }

    private UserResponse mapToResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
