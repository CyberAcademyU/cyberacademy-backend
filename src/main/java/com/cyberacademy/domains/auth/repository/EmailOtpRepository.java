package com.cyberacademy.domains.auth.repository;

import com.cyberacademy.domains.auth.model.EmailOtp;
import com.cyberacademy.domains.auth.model.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailOtp> findTopByUserIdAndTypeOrderByCreatedAtDescIdDesc(UUID userId, OtpType type);

    /** Códigos todavía vigentes de un usuario (para invalidarlos al reenviar uno nuevo). */
    List<EmailOtp> findByUserIdAndTypeAndIsUsedFalse(UUID userId, OtpType type);
}