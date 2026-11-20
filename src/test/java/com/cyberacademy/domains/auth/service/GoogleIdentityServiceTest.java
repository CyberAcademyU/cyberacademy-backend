package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.security.PasswordService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleIdentityServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleIdentityService service = new GoogleIdentityService(users, mock(PasswordService.class), "web");
    private final Jwt identity = Jwt.withTokenValue("test").header("alg", "RS256")
            .subject("google-subject").claim("email", "alumno@gmail.com").build();

    @Test void matchingEmailDoesNotGrantExistingAccount() {
        when(users.existsByEmailIgnoreCase("alumno@gmail.com")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.resolve(identity, null))
                .getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void suspendedLinkedAccountCannotLogIn() {
        User user = new User();
        user.setEmailVerified(true);
        user.setStatus(UserStatus.SUSPENDED);
        when(users.findByGoogleSubject("google-subject")).thenReturn(Optional.of(user));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.resolve(identity, null))
                .getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }
}
