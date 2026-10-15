package com.studentmanagement.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Set;

/**
 * Reads the access token from its HttpOnly cookie instead of the
 * Authorization header.
 *
 * The public auth endpoints are skipped so that a stale or expired access
 * cookie can never block signing in, refreshing, or signing out.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/csrf"
    );

    private final AuthCookieService cookies;

    public CookieBearerTokenResolver(AuthCookieService cookies) {
        this.cookies = cookies;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        return cookies.readAccessToken(request).orElse(null);
    }
}
