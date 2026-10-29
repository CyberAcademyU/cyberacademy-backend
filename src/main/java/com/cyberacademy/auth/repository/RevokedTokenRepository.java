package com.cyberacademy.auth.repository;

import com.cyberacademy.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    Optional<RevokedToken> findByJti(String jti);

    boolean existsByJti(String jti);

    Optional<RevokedToken> findByUserIdAndExpiresAtAfter(UUID userId, OffsetDateTime now);
}
