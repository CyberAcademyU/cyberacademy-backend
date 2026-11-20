package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserRole;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import com.cyberacademy.security.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Control de sesión del stream SSE. Cada heartbeat debe resolverse con una sola consulta
 * (usuario + revocación) y con la misma semántica que el filtro JWT.
 */
class ModuleStreamServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CourseModuleRepository modules = mock(CourseModuleRepository.class);
    private final SessionService jwtService = mock(SessionService.class);
    private final ModuleStreamService service = new ModuleStreamService(
            users, modules, jwtService, mock(PlatformTransactionManager.class));

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private static User student() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setRole(UserRole.STUDENT);
        return user;
    }

    private static ModuleStreamService.Connection connection(UUID userId, String jti, Instant expiresAt) {
        return new ModuleStreamService.Connection(userId, jti, expiresAt, Instant.now());
    }

    @Test
    void acceptsActiveVerifiedStudentWithLiveToken() {
        User user = student();
        when(jwtService.authenticate("jti-1")).thenReturn(user);

        assertTrue(service.authorized(connection(user.getId(), "jti-1", Instant.now().plusSeconds(600))));

        // Una única consulta por comprobación de sesión.
        verify(jwtService, times(1)).authenticate("jti-1");
    }

    @Test
    void rejectsTokenWithoutJtiWithoutHittingDatabase() {
        assertFalse(service.authorized(connection(UUID.randomUUID(), null, Instant.now().plusSeconds(600))));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsExpiredTokenWithoutHittingDatabase() {
        assertFalse(service.authorized(connection(UUID.randomUUID(), "jti-1", Instant.now().minusSeconds(1))));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsRevokedOrUnknownSession() {
        UUID userId = UUID.randomUUID();
        when(jwtService.authenticate("jti-1")).thenReturn(null);

        assertFalse(service.authorized(connection(userId, "jti-1", Instant.now().plusSeconds(600))));
    }

    @Test
    void rejectsSuspendedUser() {
        User user = student();
        user.setStatus(UserStatus.SUSPENDED);
        when(jwtService.authenticate("jti-1")).thenReturn(user);

        assertFalse(service.authorized(connection(user.getId(), "jti-1", Instant.now().plusSeconds(600))));
    }

    @Test
    void rejectsUserWithUnverifiedEmail() {
        User user = student();
        user.setEmailVerified(false);
        when(jwtService.authenticate("jti-1")).thenReturn(user);

        assertFalse(service.authorized(connection(user.getId(), "jti-1", Instant.now().plusSeconds(600))));
    }

    @Test
    void rejectsNonStudentRole() {
        User user = student();
        user.setRole(UserRole.ADMIN);
        when(jwtService.authenticate("jti-1")).thenReturn(user);

        assertFalse(service.authorized(connection(user.getId(), "jti-1", Instant.now().plusSeconds(600))));
    }
}
