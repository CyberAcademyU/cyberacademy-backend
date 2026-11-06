package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class PendingRegistrationCleanup {
    private final UserRepository users;

    public PendingRegistrationCleanup(UserRepository users) {
        this.users = users;
    }

    @Scheduled(fixedDelayString = "${app.pending-registration-cleanup-ms:3600000}")
    @Transactional
    public void deleteExpiredRegistrations() {
        users.deleteExpiredPending(UserStatus.PENDING_VERIFICATION, OffsetDateTime.now().minusHours(24));
    }
}
