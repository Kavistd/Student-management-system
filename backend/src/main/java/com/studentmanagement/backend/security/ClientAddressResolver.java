package com.studentmanagement.backend.security;

import com.studentmanagement.backend.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * The client address used for rate limiting.
 *
 * <p>Behind the Next.js proxy every request arrives from the proxy, so the
 * proxy-appended (last) X-Forwarded-For entry identifies the real client.
 * The last entry is used because earlier entries can be supplied by the
 * client itself. Trusting the header is only safe when the API is not
 * reachable except through the proxy.
 */
@Component
public class ClientAddressResolver {

    private final boolean trustForwardedFor;

    public ClientAddressResolver(AuthProperties properties) {
        this.trustForwardedFor = properties.rateLimit().trustForwardedFor();
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String last = hops[hops.length - 1].trim();
                if (!last.isEmpty()) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
