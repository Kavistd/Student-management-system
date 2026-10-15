
package com.studentmanagement.backend.service.impl;

import com.studentmanagement.backend.dto.request.UserRequest;
import com.studentmanagement.backend.dto.response.UserResponse;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.ForbiddenOperationException;
import com.studentmanagement.backend.exception.ResourceNotFoundException;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.service.UserService;
import com.studentmanagement.backend.util.EmailNormalizer;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(UserRequest request) {

        Role role = requireNonAdminRole(request.getRole());
        String email = EmailNormalizer.normalize(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException(
                    "Email already exists: " + email
            );
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        return mapToResponse(userRepository.save(user));
    }

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
    public UserResponse updateUser(Long id, UserRequest request) {

        User user = findUser(id);
        requireNotAdminAccount(user);
        Role role = requireNonAdminRole(request.getRole());
        String email = EmailNormalizer.normalize(request.getEmail());

        boolean takenByAnother = userRepository.findAllByEmailIgnoreCase(email).stream()
                .anyMatch(existingUser -> !existingUser.getId().equals(id));
        if (takenByAnother) {
            throw new IllegalArgumentException(
                    "Email already exists: " + email
            );
        }

        user.setUsername(request.getUsername().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        return mapToResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long id) {

        User user = findUser(id);
        requireNotAdminAccount(user);
        userRepository.delete(user);
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
            throw new IllegalArgumentException(
                    "Invalid role. Allowed values: ADMIN, STUDENT"
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
