package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.RefreshTokenEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    Optional<RefreshTokenEntity> findByToken(String token);

    Optional<RefreshTokenEntity> findByUser(UserEntity user);

    void deleteByUser(UserEntity user);

    /**
     * Atomically replace the presented refresh token string. Returns 0 when the
     * presented token is already gone (reuse or concurrent rotate lost the race).
     * Schema remains one row per user ({@code @OneToOne}); concurrency is enforced
     * by matching on the opaque token value, not by row locking.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity e SET e.token = :newToken, e.expiryDate = :expiry "
            + "WHERE e.token = :presentedToken")
    int rotatePresentedToken(
            @Param("presentedToken") String presentedToken,
            @Param("newToken") String newToken,
            @Param("expiry") Instant expiry
    );
}
