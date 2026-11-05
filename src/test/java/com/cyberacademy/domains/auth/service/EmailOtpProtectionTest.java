package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.dto.VerifyOtpRequest;
import com.cyberacademy.domains.auth.dto.ResendOtpRequest;
import com.cyberacademy.domains.auth.model.EmailOtp;
import com.cyberacademy.domains.auth.model.OtpType;
import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.EmailOtpRepository;
import com.cyberacademy.domains.auth.repository.RevokedTokenRepository;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.security.JwtService;
import com.cyberacademy.security.OtpProtectionService;
import com.cyberacademy.security.PasswordService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOtpProtectionTest {
    private final OtpProtectionService protection = new OtpProtectionService("test-secret-at-least-32-bytes-long");
    private final UserRepository users = mock(UserRepository.class);
    private final EmailOtpRepository otps = mock(EmailOtpRepository.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final EmailService email = mock(EmailService.class);
    private final EmailAddressVerifier verifier = mock(EmailAddressVerifier.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final AuthService service = new AuthService(users, otps, mock(RevokedTokenRepository.class),
            limits, passwords, protection, mock(JwtService.class),
            email, verifier);

    @Test
    void protectedCodeDependsOnSecretUserAndCode() {
        UUID userId = UUID.randomUUID();
        String hash = protection.protect(userId, "012345");
        assertEquals(64, hash.length());
        assertNotEquals("012345", hash);
        assertTrue(protection.matches(userId, "012345", hash));
        assertFalse(protection.matches(userId, "012346", hash));
        assertFalse(protection.matches(UUID.randomUUID(), "012345", hash));
        assertFalse(new OtpProtectionService("different-secret-at-least-32-bytes").matches(userId, "012345", hash));
    }

    @Test
    void resendStoresProtectedCodeAndSendsOriginal() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ana");
        when(limits.isAllowed(anyString())).thenReturn(true);
        when(verifier.verify("test@example.com")).thenReturn(new EmailAddressVerifier.Result(true, null));
        when(users.findLockedByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail("test@example.com");

        assertTrue(service.resendOtp(request).isSuccess());

        ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(otps).save(saved.capture());
        verify(email).sendVerificationCode(eq("test@example.com"), eq("Ana"), sentCode.capture());
        assertTrue(sentCode.getValue().matches("[0-9]{6}"));
        assertNotEquals(sentCode.getValue(), saved.getValue().getOtpHash());
        assertTrue(protection.matches(user.getId(), sentCode.getValue(), saved.getValue().getOtpHash()));
    }

    @Test
    void verificationAcceptsOnlyCorrectCodeAndActivatesUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        EmailOtp otp = new EmailOtp();
        otp.setUserId(user.getId());
        otp.setOtpHash(protection.protect(user.getId(), "012345"));
        otp.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        when(limits.isAllowed(anyString())).thenReturn(true);
        when(users.findLockedByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(otps.findTopByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp));
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtpCode("012346");
        request.setPassword("new-password");
        when(passwords.hashPassword("new-password")).thenReturn("new-hash");

        assertFalse(service.verifyOtp(request).isSuccess());
        assertEquals(1, otp.getAttempts());
        assertFalse(otp.isUsed());
        request.setOtpCode("012345");
        assertTrue(service.verifyOtp(request).isSuccess());
        assertTrue(otp.isUsed());
        assertTrue(user.isEmailVerified());
        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertFalse(service.verifyOtp(request).isSuccess());
    }

    @Test
    void expiredOrSuspendedRegistrationCannotBeActivated() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setCreatedAt(OffsetDateTime.now().minusHours(25));
        when(limits.isAllowed(anyString())).thenReturn(true);
        when(users.findLockedByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtpCode("012345");
        request.setPassword("new-password");
        assertFalse(service.verifyOtp(request).isSuccess());
        user.setCreatedAt(OffsetDateTime.now());
        user.setStatus(UserStatus.SUSPENDED);
        assertFalse(service.verifyOtp(request).isSuccess());
        verifyNoInteractions(otps, passwords);
    }
}
