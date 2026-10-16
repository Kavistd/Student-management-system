package com.studentmanagement.backend.security;

import com.studentmanagement.backend.config.AuthProperties;
import com.studentmanagement.backend.entity.RefreshToken;
import com.studentmanagement.backend.entity.User;
import com.studentmanagement.backend.exception.InvalidRefreshTokenException;
import com.studentmanagement.backend.repository.RefreshTokenRepository;
import com.studentmanagement.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import static com.studentmanagement.backend.exception.InvalidRefreshTokenException.Reason.*;
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
    private final UserRepository users;
    private final EntityManager entityManager;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties,
                               UserRepository users, EntityManager entityManager) {
        this.repository = repository;
        this.properties = properties;
        this.users = users;
        this.entityManager = entityManager;
    }

    public record IssuedToken(String value, boolean persistent) {
    }

    public record Rotation(User user, IssuedToken token) {
    }

    @Transactional
    public IssuedToken issue(User user, boolean persistent) {
        User lockedUser = users.lockById(user.getId())
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID));
        Instant now = Instant.now();
        repository.deleteExpiredForUser(user.getId(), now);
        return create(lockedUser, UUID.randomUUID().toString(), persistent, now.plus(properties.refreshTokenTtl()));
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedToken) {
        RefreshToken current = lockedToken(presentedToken);
        Instant now = Instant.now();

        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException(EXPIRED);
        }
        if (current.getRevokedAt() != null) {
            boolean liveSuccessor = repository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(
                    current.getFamilyId(), now);
            // Logout/replay revokes all active family members. Never grant grace to that family.
            if (!liveSuccessor) {
                throw new InvalidRefreshTokenException(REVOKED);
            }
            if (current.getRevokedAt().plus(properties.refreshReuseGrace()).isAfter(now)) {
                throw new InvalidRefreshTokenException(CONCURRENT_ROTATION);
            }
            repository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException(REPLAYED);
        }

        current.setRevokedAt(now);
        IssuedToken next = create(current.getUser(), current.getFamilyId(),
                current.isPersistent(), current.getExpiresAt());
        return new Rotation(current.getUser(), next);
    }

    /**
     * Signs out every session of the user (e.g. after a password change) and
     * starts one new session, keeping the presented session's persistence.
     * The caller must already hold the user lock.
     */
    @Transactional
    public IssuedToken replaceAllSessions(User user, String presentedToken) {
        boolean persistent = presentedToken != null && repository.findByTokenHash(hash(presentedToken))
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .map(RefreshToken::isPersistent)
                .orElse(false);
        repository.deleteAllForUser(user.getId());
        return issue(user, persistent);
    }

    /** Revokes the session (token family) the presented token belongs to, if any. */
    @Transactional
    public void revoke(String presentedToken) {
        String tokenHash = hash(presentedToken);
        Long userId = repository.findUserIdByTokenHash(tokenHash).orElse(null);
        if (userId == null || users.lockById(userId).isEmpty()) return;
        repository.findByTokenHash(tokenHash).ifPresent(token -> {
            entityManager.refresh(token);
            repository.revokeFamily(token.getFamilyId(), Instant.now());
        });
    }

    /** Always lock user first, then reload token state inside the same transaction. */
    private RefreshToken lockedToken(String presentedToken) {
        String tokenHash = hash(presentedToken);
        Long userId = repository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID));
        users.lockById(userId).orElseThrow(() -> new InvalidRefreshTokenException(INVALID));
        RefreshToken token = repository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID));
        // Also refresh an entity already managed by an enclosing transaction.
        entityManager.refresh(token);
        return token;
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
