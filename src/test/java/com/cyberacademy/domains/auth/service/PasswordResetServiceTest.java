package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.model.PasswordResetCode;
import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.PasswordResetCodeRepository;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.security.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetCodeRepository codes = mock(PasswordResetCodeRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final EmailService email = mock(EmailService.class);
    private final PasswordResetService service = new PasswordResetService(users, codes, passwords, limits, email);
    private final User user = new User();
    private final PasswordResetCode reset = new PasswordResetCode();

    @BeforeEach void setup() {
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        reset.setUserId(user.getId());
        reset.setCodeHash("hash");
        reset.setCreatedAt(OffsetDateTime.now());
        reset.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        when(limits.isAllowed(anyString())).thenReturn(true);
    }

    @Test void nonexistentAccountDoesNotSendMail() {
        when(users.findLockedByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());
        service.request("test@example.com");
        verifyNoInteractions(email);
    }

    @Test void validCodeChangesPasswordAndCannotBeReused() {
        when(users.findLockedByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(codes.findById(user.getId())).thenReturn(Optional.of(reset));
        when(passwords.verifyPassword("123456", "hash")).thenReturn(true);
        when(passwords.hashPassword("nueva123")).thenReturn("new-hash");
        assertTrue(service.confirm("test@example.com", "123456", "nueva123"));
        assertTrue(reset.isUsed());
        assertEquals("new-hash", user.getPasswordHash());
        assertNotNull(user.getPasswordChangedAt());
        assertFalse(service.confirm("test@example.com", "123456", "nueva123"));
    }

    @Test void invalidCodeCountsAttemptsAndExpiredCodeFails() {
        when(users.findLockedByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(codes.findById(user.getId())).thenReturn(Optional.of(reset));
        reset.setAttempts(4);
        assertFalse(service.confirm("test@example.com", "000000", "nueva123"));
        assertEquals(5, reset.getAttempts());
        assertTrue(reset.isUsed());
        reset.setUsed(false);
        reset.setAttempts(0);
        reset.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertFalse(service.confirm("test@example.com", "123456", "nueva123"));
        verify(passwords, never()).hashPassword(anyString());
    }
}
