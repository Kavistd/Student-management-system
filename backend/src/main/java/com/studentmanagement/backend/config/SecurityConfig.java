package com.studentmanagement.backend.config;

import com.studentmanagement.backend.security.AuthCookieService;
import com.studentmanagement.backend.repository.UserRepository;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import com.studentmanagement.backend.security.CookieBearerTokenResolver;
import com.studentmanagement.backend.security.JsonSecurityErrorHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Stateless, cookie-based JWT security.
 *
 * <p>The JWT filter is registered manually rather than through
 * {@code oauth2ResourceServer()}: that DSL exempts every request carrying a
 * bearer token from CSRF protection, which is right for Authorization headers
 * but would disable CSRF entirely when the token travels in a cookie.
 *
 * <p>The security context lives only in request attributes. Besides keeping
 * the API stateless, this lets session management recognise a JWT-authenticated
 * request as already authenticated; otherwise it would treat every request as a
 * new sign-in and rotate the CSRF token each time. The token is instead rotated
 * explicitly at sign-in and sign-out (AuthController).
 *
 * <p>Anything not explicitly listed below is denied.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            AuthCookieService authCookies,
            JsonSecurityErrorHandler securityErrors,
            CookieCsrfTokenRepository csrfTokens,
            UserRepository users
    ) throws Exception {

        http
            .csrf(csrf -> csrf
                .spa()
                .csrfTokenRepository(csrfTokens))
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context
                .securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(securityErrors)
                .accessDeniedHandler(securityErrors))
            .addFilterBefore(
                jwtCookieFilter(jwtDecoder, authCookies, securityErrors, users),
                AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Lets error responses for already-authorized requests render.
                .requestMatchers("/error").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/register",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/password").hasRole("STUDENT")

                // Student accounts and profiles are created only by public registration.
                .requestMatchers(HttpMethod.POST, "/api/students", "/api/students/**",
                    "/api/users", "/api/users/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/students/me").hasRole("STUDENT")
                .requestMatchers(HttpMethod.PUT, "/api/students/me").hasRole("STUDENT")

                .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/{id}")
                    .hasAnyRole("ADMIN", "STUDENT")
                .requestMatchers("/api/courses/**").hasRole("ADMIN")
                .requestMatchers("/api/students/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                .anyRequest().denyAll()
            );

        return http.build();
    }

    private static BearerTokenAuthenticationFilter jwtCookieFilter(
            JwtDecoder jwtDecoder,
            AuthCookieService authCookies,
            JsonSecurityErrorHandler securityErrors, UserRepository users) {

        // "role": "ADMIN" -> authority ROLE_ADMIN, matched by hasRole("ADMIN").
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwt -> {
            // Runs only after signature, issuer and lifetime validation; never use request IDs.
            Long userId;
            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException ex) {
                throw new InvalidBearerTokenException("Invalid access token.");
            }
            if (!users.existsById(userId)) throw new InvalidBearerTokenException("Account is no longer available.");
            return converter.convert(jwt);
        });

        BearerTokenAuthenticationFilter filter =
                new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        filter.setBearerTokenResolver(new CookieBearerTokenResolver(authCookies));
        filter.setAuthenticationEntryPoint(securityErrors);
        filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
        return filter;
    }
}
