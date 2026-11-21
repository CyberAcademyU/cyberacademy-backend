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
            .subject("google-subject").claim("email", "alumno@gmail.com")
            .claim("given_name", "Nombre de Google").claim("family_name", "Apellido de Google").build();

    @Test void matchingEmailDoesNotGrantExistingAccount() {
        when(users.existsByEmailIgnoreCase("alumno@gmail.com")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.resolve(identity, null, null, null))
                .getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void suspendedLinkedAccountCannotLogIn() {
        User user = new User();
        user.setEmailVerified(true);
        user.setStatus(UserStatus.SUSPENDED);
        when(users.findByGoogleSubject("google-subject")).thenReturn(Optional.of(user));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.resolve(identity, null, null, null))
                .getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void newAccountNeedsNamesBeforeSavingAnything() {
        assertEquals(428, assertThrows(ResponseStatusException.class,
                () -> service.resolve(identity, null, null, null)).getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void rejectsBlankOrInvalidNames() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.resolve(identity, null, "  ", "Pérez")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.resolve(identity, null, "Ana", null)).getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void usesConfirmedNamesInsteadOfGoogleClaims() {
        when(users.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        User created = service.resolve(identity, null, "  María José  ", "  Pérez Díaz  ");
        assertEquals("María José", created.getFirstName());
        assertEquals("Pérez Díaz", created.getLastName());
        assertEquals(UserRole.STUDENT, created.getRole());
        assertTrue(created.isEmailVerified());
        assertEquals("google-subject", created.getGoogleSubject());
    }

    @Test void existingGoogleAccountDoesNotNeedNamesAndIsNotRenamed() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setFirstName("Ana");
        user.setLastName("Pérez");
        when(users.findByGoogleSubject("google-subject")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);
        assertSame(user, service.resolve(identity, null, null, null));
        assertSame(user, service.resolve(identity, null, "Otro", "Nombre"));
        assertEquals("Ana", user.getFirstName());
        assertEquals("Pérez", user.getLastName());
    }
}
