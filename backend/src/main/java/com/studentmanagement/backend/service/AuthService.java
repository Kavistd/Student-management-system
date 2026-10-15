
package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.RegisterRequest;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.Student;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();
        String username = request.username().trim();

        if (userRepository.existsByEmail(email)
                || studentRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username is already taken"
            );
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.STUDENT);

        User savedUser = userRepository.save(user);

        Student student = new Student();
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setEmail(email);
        student.setUser(savedUser);

        studentRepository.save(student);

        return savedUser.getId();
    }
}
