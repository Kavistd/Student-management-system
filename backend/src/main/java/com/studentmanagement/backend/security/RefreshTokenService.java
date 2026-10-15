package com.studentmanagement.backend.security;

import com.studentmanagement.backend.config.AuthProperties;
import com.studentmanagement.backend.entity.RefreshToken;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.InvalidRefreshTokenException;
import com.studentmanagement.backend.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens: 256 random bits, stored only as SHA-256 hashes.
 *
 * <p>Every refresh rotates the token. Presenting a token that was already
 * rotated signals theft, so its whole family is revoked; a second use within
 * a short grace window is instead treated as two tabs refreshing at once.
 * Expiry is absolute: rotation keeps the original sign-in's expiry, so a
 * session never outlives the refresh TTL without signing in again.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public record IssuedToken(String value, boolean persistent) {
    }

    public record Rotation(User user, IssuedToken token) {
    }

    @Transactional
    public IssuedToken issue(User user, boolean persistent) {
        Instant now = Instant.now();
        repository.deleteExpiredForUser(user.getId(), now);
        return create(user, UUID.randomUUID().toString(), persistent, now.plus(properties.refreshTokenTtl()));
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedToken) {
        RefreshToken current = repository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new InvalidRefreshTokenException(false));
        Instant now = Instant.now();

        if (current.getRevokedAt() != null) {
            if (current.getRevokedAt().plus(properties.refreshReuseGrace()).isAfter(now)) {
                // Another tab rotated this token a moment ago; its cookies are already current.
                throw new InvalidRefreshTokenException(true);
            }
            repository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException(false);
        }

        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException(false);
        }

        current.setRevokedAt(now);
        IssuedToken next = create(current.getUser(), current.getFamilyId(),
                current.isPersistent(), current.getExpiresAt());
        return new Rotation(current.getUser(), next);
    }

    /** Revokes the session (token family) the presented token belongs to, if any. */
    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHash(hash(presentedToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private IssuedToken create(User user, String familyId, boolean persistent, Instant expiresAt) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(value));
        token.setFamilyId(familyId);
        token.setPersistent(persistent);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(expiresAt);
        repository.save(token);

        return new IssuedToken(value, persistent);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
