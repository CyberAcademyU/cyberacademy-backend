package com.cyberacademy.auth.repository;

import com.cyberacademy.auth.entity.EmailOtp;
import com.cyberacademy.auth.entity.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailOtp> findTopByUserIdAndTypeOrderByCreatedAtDescIdDesc(UUID userId, OtpType type);

    @Modifying
    @Query("update EmailOtp o set o.isUsed = true where o.userId = :userId and o.type = :type and o.isUsed = false")
    int invalidatePrevious(@Param("userId") UUID userId, @Param("type") OtpType type);
}