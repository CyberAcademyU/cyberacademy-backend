package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.controller.AuthController;
import com.cyberacademy.domains.auth.repository.EmailOtpRepository;
import com.cyberacademy.domains.auth.repository.PasswordResetCodeRepository;
import com.cyberacademy.domains.auth.repository.RevokedTokenRepository;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.exception.GlobalExceptionHandler;
import com.cyberacademy.exception.RateLimitException;
import com.cyberacademy.security.JwtService;
import com.cyberacademy.security.OtpProtectionService;
import com.cyberacademy.security.PasswordService;
import com.cyberacademy.security.SecurityConfig;
import com.cyberacademy.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateLimitServiceTest {
    @Test
    void corsExposesRetryAfterToFrontend() {
        SecurityConfig config = new SecurityConfig(mock(JwtService.class), mock(RevokedTokenRepository.class),
                mock(JwtAuthenticationFilter.class), "https://frontend.example.com");
        var request = new MockHttpServletRequest("POST", "/auth/login");
        assertTrue(config.corsConfigurationSource().getCorsConfiguration(request).getExposedHeaders().contains("Retry-After"));
    }

    @Test
    void reportsRemainingTimeAndAllowsRequestsAfterWindow() {
        AtomicLong now = new AtomicLong(1_000_000);
        RateLimitService limits = new RateLimitService(now::get);
        for (int i = 0; i < 5; i++) limits.check("login:test@example.com");
        assertEquals(900, assertThrows(RateLimitException.class,
                () -> limits.check("login:test@example.com")).getRetryAfterSeconds());
        now.addAndGet(899_001);
        assertEquals(1, assertThrows(RateLimitException.class,
                () -> limits.check("login:test@example.com")).getRetryAfterSeconds());
        now.addAndGet(999);
        assertDoesNotThrow(() -> limits.check("login:test@example.com"));
    }

    @Test
    void limitsOriginAcrossDifferentAddressesButSeparatesOperations() {
        RateLimitService limits = new RateLimitService();
        for (int i = 0; i < 50; i++) limits.check("login:user" + i + "@example.com");
        assertThrows(RateLimitException.class, () -> limits.check("login:other@example.com"));
        assertDoesNotThrow(() -> limits.check("register:other@example.com"));
    }

    @Test
    void loginAndPasswordResetReturn429WithRetryAfter() throws Exception {
        RateLimitService limits = new RateLimitService(() -> 1_000_000);
        UserRepository users = mock(UserRepository.class);
        EmailService email = mock(EmailService.class);
        PasswordService passwords = mock(PasswordService.class);
        AuthService auth = new AuthService(users, mock(EmailOtpRepository.class), mock(RevokedTokenRepository.class),
                mock(RevokedTokenCache.class), limits, passwords, mock(OtpProtectionService.class), mock(JwtService.class), email,
                mock(EmailAddressVerifier.class));
        PasswordResetService reset = new PasswordResetService(users, mock(PasswordResetCodeRepository.class), passwords, limits, email);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, reset))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\",\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"));
    }
}
