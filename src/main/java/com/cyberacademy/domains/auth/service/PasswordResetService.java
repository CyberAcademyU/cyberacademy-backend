package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.model.PasswordResetCode;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.PasswordResetCodeRepository;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.exception.EmailDeliveryException;
import com.cyberacademy.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final PasswordService passwords;
    private final RateLimitService limits;
    private final EmailService email;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes, PasswordService passwords,
                                RateLimitService limits, EmailService email) {
        this.users = users;
        this.codes = codes;
        this.passwords = passwords;
        this.limits = limits;
        this.email = email;
    }

    @Transactional
    public void request(String address) {
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        if (!limits.isAllowed("reset-request:" + normalized)) return;
        var user = users.findLockedByEmailIgnoreCase(normalized).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) return;
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        var reset = codes.findById(user.getId()).orElseGet(PasswordResetCode::new);
        reset.setUserId(user.getId());
        reset.setCodeHash(passwords.hashPassword(code));
        reset.setCreatedAt(OffsetDateTime.now());
        reset.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        reset.setAttempts(0);
        reset.setUsed(false);
        reset = codes.save(reset);
        try {
            email.sendPasswordResetCode(normalized, code);
        } catch (EmailDeliveryException ex) {
            reset.setUsed(true);
            log.warn("No se pudo entregar un código de recuperación.");
        }
    }

    @Transactional
    public boolean confirm(String address, String code, String newPassword) {
        String normalized = address.strip().toLowerCase(Locale.ROOT);
        if (!limits.isAllowed("reset-confirm:" + normalized)) return false;
        var user = users.findLockedByEmailIgnoreCase(normalized).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) return false;
        var reset = codes.findById(user.getId()).orElse(null);
        if (reset == null || reset.isUsed() || reset.getAttempts() >= 5 || !reset.getExpiresAt().isAfter(OffsetDateTime.now())) return false;
        if (user.getPasswordChangedAt() != null && !reset.getCreatedAt().isAfter(user.getPasswordChangedAt())) return false;
        if (!passwords.verifyPassword(code, reset.getCodeHash())) {
            reset.setAttempts(reset.getAttempts() + 1);
            if (reset.getAttempts() >= 5) reset.setUsed(true);
            return false;
        }
        reset.setUsed(true);
        var now = OffsetDateTime.now();
        user.setPasswordHash(passwords.hashPassword(newPassword));
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        return true;
    }
}
