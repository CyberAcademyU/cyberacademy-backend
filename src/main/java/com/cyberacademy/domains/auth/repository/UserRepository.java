package com.cyberacademy.domains.auth.repository;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, UUID id);

    /**
     * Usuario de la sesión, solo si su token no está revocado.
     * Sustituye a existsByJti() + findById(): una consulta en lugar de dos por petición.
     */
    @Query("""
            select u from User u
            where u.id = :id
              and not exists (select 1 from RevokedToken r where r.jti = :jti)
            """)
    Optional<User> findSessionUser(@Param("id") UUID id, @Param("jti") String jti);

    @Modifying
    @Query("delete from User u where u.status = :status and u.emailVerified = false and u.createdAt < :cutoff")
    int deleteExpiredPending(@Param("status") UserStatus status, @Param("cutoff") OffsetDateTime cutoff);
}
