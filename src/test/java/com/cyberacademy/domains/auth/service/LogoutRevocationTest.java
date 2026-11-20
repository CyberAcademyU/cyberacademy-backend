package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.model.RevokedToken;
import com.cyberacademy.domains.auth.repository.EmailOtpRepository;
import com.cyberacademy.domains.auth.repository.RevokedTokenRepository;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.security.JwtService;
import com.cyberacademy.security.OtpProtectionService;
import com.cyberacademy.security.PasswordService;
import com.cyberacademy.security.PasswordTransportService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LogoutRevocationTest {
    private final RevokedTokenRepository revoked = mock(RevokedTokenRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService service = new AuthService(mock(UserRepository.class), mock(EmailOtpRepository.class),
            revoked, mock(RateLimitService.class), mock(PasswordService.class), mock(OtpProtectionService.class), jwt,
            mock(EmailService.class), mock(EmailAddressVerifier.class),
            new PasswordTransportService("test-secret-at-least-32-bytes-long", false));

    @Test
    void validTokenIsRecordedForRevocation() {
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(jwt.getClaimsFromToken("token")).thenReturn(claims);
        when(claims.getId()).thenReturn("token-id");
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.getExpiration()).thenReturn(Date.from(Instant.parse("2030-01-01T00:00:00Z")));

        service.revokeToken("token");

        ArgumentCaptor<RevokedToken> saved = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revoked).save(saved.capture());
        assertEquals("token-id", saved.getValue().getJti());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(Instant.parse("2030-01-01T00:00:00Z"), saved.getValue().getExpiresAt().toInstant());
    }

    @Test
    void invalidTokenDoesNotAttemptPersistence() {
        when(jwt.getClaimsFromToken("invalid")).thenThrow(new JwtException("invalid"));
        service.revokeToken("invalid");
        verifyNoInteractions(revoked);
    }

    @Test
    void databaseFailureIsNotReportedAsSuccessfulRevocation() {
        Claims claims = mock(Claims.class);
        when(jwt.getClaimsFromToken("token")).thenReturn(claims);
        when(claims.getId()).thenReturn("token-id");
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(claims.getExpiration()).thenReturn(new Date());
        when(revoked.save(any())).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(DataAccessResourceFailureException.class, () -> service.revokeToken("token"));
    }
}
