package com.studentmanagement.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Double-submit CSRF token store: a script-readable XSRF-TOKEN cookie that the
 * frontend echoes in the X-XSRF-TOKEN header. Shared by the security filter
 * chain and the auth endpoints, which rotate the token on sign-in and sign-out.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CsrfConfig {

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/"));
        return repository;
    }
}
