package com.studentmanagement.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Authentication settings, bound from {@code app.auth.*}.
 * Secrets and environment-specific values come from environment variables
 * (see application.properties); nothing sensitive has a default.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(

        /* Base64-encoded HMAC key for signing access tokens (JWT_SECRET). */
        @NotBlank(message = "app.auth.jwt-secret (JWT_SECRET) must be set")
        String jwtSecret,

        @DefaultValue("student-management-system")
        String issuer,

        @DefaultValue("15m")
        Duration accessTokenTtl,

        @DefaultValue("7d")
        Duration refreshTokenTtl,

        /*
         * How long after a refresh token was rotated a second use is treated as
         * a benign race between tabs rather than token theft.
         */
        @DefaultValue("10s")
        Duration refreshReuseGrace,

        /* Adds the Secure attribute (and __Host-/__Secure- name prefixes). True outside local HTTP development. */
        @DefaultValue("true")
        boolean cookieSecure,

        @Valid
        @DefaultValue
        RateLimit rateLimit
) {

    public record RateLimit(

            @Min(1)
            @DefaultValue("5")
            int maxFailuresPerEmail,

            @Min(1)
            @DefaultValue("20")
            int maxFailuresPerClient,

            @DefaultValue("15m")
            Duration window,

            /*
             * Use the last X-Forwarded-For entry as the client address. Only safe
             * when the API is reachable exclusively through the Next.js proxy.
             */
            @DefaultValue("true")
            boolean trustForwardedFor
    ) {
    }
}
