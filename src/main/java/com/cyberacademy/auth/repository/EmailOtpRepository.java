package com.cyberacademy.auth.repository;

import com.cyberacademy.auth.entity.EmailOtp;
import com.cyberacademy.auth.entity.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {
    Optional<EmailOtp> findTopByEmailIgnoreCaseAndTypeAndIsUsedFalseOrderByCreatedAtDesc(String email, OtpType type);
}