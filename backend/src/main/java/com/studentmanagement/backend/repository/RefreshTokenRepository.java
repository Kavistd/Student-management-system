package com.studentmanagement.backend.repository;

import com.studentmanagement.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    // Scalar lookup avoids caching token state before the user lock is acquired.
    @Query("select t.user.id from RefreshToken t where t.tokenHash = :hash")
    Optional<Long> findUserIdByTokenHash(@Param("hash") String hash);

    boolean existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(String familyId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.user.id = :userId and t.expiresAt < :now")
    int deleteExpiredForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
