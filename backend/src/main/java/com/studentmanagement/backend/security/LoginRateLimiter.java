package com.studentmanagement.backend.security;

import com.studentmanagement.backend.config.AuthProperties;
import com.studentmanagement.backend.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit on failed sign-ins, per email and per client address.
 * While either limit is reached every attempt is refused, even with the right
 * password, so guessing cannot continue.
 *
 * <p>State is in memory and per instance: it resets on restart and is not
 * shared across multiple backend instances.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final AuthProperties.RateLimit limits;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(AuthProperties properties) {
        this.limits = properties.rateLimit();
    }

    /** Throws when the email or the client has too many recent failures. */
    public void check(String email, String clientKey) {
        Instant now = Instant.now();
        long retryAfter = Math.max(
                secondsUntilAllowed(emailKey(email), limits.maxFailuresPerEmail(), now),
                secondsUntilAllowed(clientKey(clientKey), limits.maxFailuresPerClient(), now));
        if (retryAfter > 0) {
            throw new RateLimitExceededException(retryAfter);
        }
    }

    public void recordFailure(String email, String clientKey) {
        Instant now = Instant.now();
        if (failures.size() > MAX_TRACKED_KEYS) {
            evictStale(now);
        }
        record(emailKey(email), now);
        record(clientKey(clientKey), now);
    }

    /** A successful sign-in clears that email's failures (the client's stay counted). */
    public void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    private void record(String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private long secondsUntilAllowed(String key, int max, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() < max) {
                return 0;
            }
            Instant oldest = attempts.peekFirst();
            long seconds = Duration.between(now, oldest.plus(limits.window())).toSeconds();
            return Math.max(seconds, 1);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(limits.window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    private void evictStale(Instant now) {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static String emailKey(String email) {
        return "email:" + email;
    }

    private static String clientKey(String clientKey) {
        return "client:" + clientKey;
    }
}
