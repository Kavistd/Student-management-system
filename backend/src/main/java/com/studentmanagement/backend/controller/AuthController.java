package com.studentmanagement.backend.controller;

import com.studentmanagement.backend.dto.request.ChangePasswordRequest;
import com.studentmanagement.backend.dto.request.LoginRequest;
import com.studentmanagement.backend.dto.request.RegisterRequest;
import com.studentmanagement.backend.dto.response.AuthUserResponse;
import com.studentmanagement.backend.exception.InvalidRefreshTokenException;
import com.studentmanagement.backend.security.AuthCookieService;
import com.studentmanagement.backend.security.ClientAddressResolver;
import com.studentmanagement.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Session endpoints. Tokens only ever travel in HttpOnly cookies; response
 * bodies carry the user profile, never a token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookies;
    private final ClientAddressResolver clientAddresses;
    private final CsrfTokenRepository csrfTokens;

    public AuthController(
            AuthService authService,
            AuthCookieService authCookies,
            ClientAddressResolver clientAddresses,
            CsrfTokenRepository csrfTokens
    ) {
        this.authService = authService;
        this.authCookies = authCookies;
        this.clientAddresses = clientAddresses;
        this.csrfTokens = csrfTokens;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        Long userId = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                    "message", "Student registered successfully",
                    "userId", userId
                ));
    }

    /** Issues the readable XSRF-TOKEN cookie the frontend echoes on unsafe requests. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken(); // loading the deferred token writes the cookie
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public AuthUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthService.Session session =
                authService.login(request, clientAddresses.resolve(httpRequest));
        writeSession(httpResponse, session);
        rotateCsrfToken(httpRequest, httpResponse);
        return session.user();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Optional<String> refreshToken = authCookies.readRefreshToken(httpRequest);
        if (refreshToken.isEmpty()) {
            authCookies.clearSessionCookies(httpResponse);
            return sessionExpired();
        }

        try {
            AuthService.Session session = authService.refresh(refreshToken.get());
            writeSession(httpResponse, session);
            return ResponseEntity.ok(session.user());
        } catch (InvalidRefreshTokenException ex) {
            if (ex.isConcurrentRotation()) {
                // The winning response may still be in flight. Do not clear its shared cookies.
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "code", "REFRESH_RACE",
                        "message", "Another request refreshed your session. Please retry shortly."));
            }
            authCookies.clearSessionCookies(httpResponse);
            return sessionExpired();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        authCookies.readRefreshToken(httpRequest).ifPresent(authService::logout);
        authCookies.clearSessionCookies(httpResponse);
        rotateCsrfToken(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    /** Changes the signed-in student's password; all other sessions are signed out. */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthService.Session session = authService.changePassword(
                Long.valueOf(jwt.getSubject()),
                request,
                clientAddresses.resolve(httpRequest),
                authCookies.readRefreshToken(httpRequest).orElse(null));
        writeSession(httpResponse, session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.valueOf(jwt.getSubject()));
    }

    private void writeSession(HttpServletResponse response, AuthService.Session session) {
        authCookies.setSessionCookies(
                response,
                session.accessToken(),
                session.refreshToken().value(),
                session.refreshToken().persistent());
    }

    /** A fresh CSRF token whenever the signed-in identity changes (prevents token fixation). */
    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        csrfTokens.saveToken(null, request, response);
        csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
    }

    private static ResponseEntity<Map<String, String>> sessionExpired() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "code", "SESSION_EXPIRED",
                    "message", "Your session has ended. Please sign in again."));
    }
}
