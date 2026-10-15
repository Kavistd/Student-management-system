package com.studentmanagement.backend.security;

import com.studentmanagement.backend.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Writes and reads the HttpOnly session cookies.
 *
 * <ul>
 *   <li>Access token: SameSite=Lax, Path=/, lives as long as the JWT.</li>
 *   <li>Refresh token: SameSite=Strict, Path=/api/auth so it is only sent to the
 *       auth endpoints. Persistent only when "Remember me" was chosen; otherwise a
 *       browser-session cookie. The server enforces expiry either way.</li>
 * </ul>
 * With Secure enabled the names use the __Host-/__Secure- prefixes, which
 * browsers only accept over HTTPS with the matching attributes.
 */
@Component
public class AuthCookieService {

    static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public String accessCookieName() {
        return properties.cookieSecure() ? "__Host-access_token" : "access_token";
    }

    public String refreshCookieName() {
        return properties.cookieSecure() ? "__Secure-refresh_token" : "refresh_token";
    }

    public void setSessionCookies(HttpServletResponse response,
                                  String accessToken,
                                  String refreshToken,
                                  boolean persistent) {

        add(response, accessCookie(accessToken, properties.accessTokenTtl()));
        add(response, refreshCookie(refreshToken, persistent ? properties.refreshTokenTtl() : null));
    }

    public void clearSessionCookies(HttpServletResponse response) {
        add(response, accessCookie("", Duration.ZERO));
        add(response, refreshCookie("", Duration.ZERO));
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return read(request, accessCookieName());
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return read(request, refreshCookieName());
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(accessCookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie refreshCookie(String value, Duration maxAgeOrSession) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(refreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH);
        if (maxAgeOrSession != null) {
            builder.maxAge(maxAgeOrSession);
        }
        return builder.build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }
}
