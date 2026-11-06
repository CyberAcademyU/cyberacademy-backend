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

    @Modifying
    @Query("delete from User u where u.status = :status and u.emailVerified = false and u.createdAt < :cutoff")
    int deleteExpiredPending(@Param("status") UserStatus status, @Param("cutoff") OffsetDateTime cutoff);
}
