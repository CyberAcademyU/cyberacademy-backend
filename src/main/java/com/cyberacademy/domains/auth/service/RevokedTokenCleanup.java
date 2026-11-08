package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Limpieza programada de tokens revocados.
 *
 * Un token revocado solo necesita estar en la tabla hasta su expiración natural
 * (el filtro JWT ya lo rechaza igualmente por expirada). Si no se borran, la
 * tabla crece indefinidamente y degrada la consulta existsByJti del filtro.
 */
@Service
public class RevokedTokenCleanup {
    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanup.class);

    private final RevokedTokenRepository revokedTokens;

    public RevokedTokenCleanup(RevokedTokenRepository revokedTokens) {
        this.revokedTokens = revokedTokens;
    }

    @Scheduled(fixedDelayString = "${app.revoked-token-cleanup-ms:3600000}")
    @Transactional
    public void deleteExpiredRevocations() {
        long deleted = revokedTokens.deleteByExpiresAtBefore(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Limpieza de revoked_tokens: {} registros con token ya expirado eliminados", deleted);
        }
    }
}
