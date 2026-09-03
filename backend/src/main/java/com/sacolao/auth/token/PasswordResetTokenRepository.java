package com.sacolao.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByUser_Id(UUID userId);

    List<PasswordResetToken> findByUser_Id(UUID userId);

    @Query("select t from PasswordResetToken t join fetch t.user u left join fetch u.establishment where t.tokenHash = :hash")
    Optional<PasswordResetToken> findWithUserByTokenHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int expireUnused(@Param("userId") UUID userId, @Param("now") Instant now);
}
