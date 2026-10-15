package com.studentmanagement.backend.service;

import com.studentmanagement.backend.dto.request.LoginRequest;
import com.studentmanagement.backend.dto.request.RegisterRequest;
import com.studentmanagement.backend.dto.response.AuthUserResponse;
import com.studentmanagement.backend.entity.Role;
import com.studentmanagement.backend.entity.Student;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.AuthenticationFailedException;
import com.studentmanagement.backend.exception.InvalidRefreshTokenException;
import com.studentmanagement.backend.repository.StudentRepository;
import com.studentmanagement.backend.repository.UserRepository;
import com.studentmanagement.backend.security.JwtService;
import com.studentmanagement.backend.security.LoginRateLimiter;
import com.studentmanagement.backend.security.RefreshTokenService;
import com.studentmanagement.backend.security.RefreshTokenService.IssuedToken;
import com.studentmanagement.backend.util.EmailNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes; longer inputs are rejected rather than truncated. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;

    /** Compared against when the email is unknown, so both failure paths cost one BCrypt check. */
    private final String unknownUserHash;

    public AuthService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginRateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** A signed-in session: the user plus the tokens to place in cookies. */
    public record Session(AuthUserResponse user, String accessToken, IssuedToken refreshToken) {
    }

    /** Public sign-up. Always creates a STUDENT; the request has no role field. */
    @Transactional
    public Long register(RegisterRequest request) {

        String email = EmailNormalizer.normalize(request.email());
        String username = request.username().trim();

        if (userRepository.existsByEmailIgnoreCase(email)
                || studentRepository.existsByEmailIgnoreCase(email)) {
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

    @Transactional
    public Session login(LoginRequest request, String clientAddress) {

        String email = EmailNormalizer.normalize(request.email());
        rateLimiter.check(email, clientAddress);

        // Emails are unique case-insensitively for new accounts; a legacy pair
        // differing only by case is ambiguous, so neither can sign in.
        List<User> matches = userRepository.findAllByEmailIgnoreCase(email);
        User user = matches.size() == 1 ? matches.getFirst() : null;

        boolean valid = passwordFits(request.password())
                && passwordEncoder.matches(request.password(),
                        user != null ? user.getPassword() : unknownUserHash)
                && user != null;

        if (!valid) {
            rateLimiter.recordFailure(email, clientAddress);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }

        rateLimiter.recordSuccess(email);
        return new Session(
                toResponse(user),
                jwtService.issueAccessToken(user),
                refreshTokenService.issue(user, request.persistentSession()));
    }

    /** Rotates the refresh token and issues a new access token. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = rotation.user();
        return new Session(toResponse(user), jwtService.issueAccessToken(user), rotation.token());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(AuthService::toResponse)
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Your account is no longer available. Please sign in again."));
    }

    private static boolean passwordFits(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private static AuthUserResponse toResponse(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name());
    }
}
