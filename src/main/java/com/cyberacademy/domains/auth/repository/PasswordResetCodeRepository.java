package com.cyberacademy.domains.auth.repository;

import com.cyberacademy.domains.auth.model.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {}
