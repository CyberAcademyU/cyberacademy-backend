package com.cyberacademy.security;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(SessionServiceTest.Config.class)
class SessionServiceTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:sessions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean SessionService sessions(JdbcTemplate db, UserRepository users) { return new SessionService(db, users); }
    }
    @Autowired SessionService sessions;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    private User user;

    @BeforeEach void setup() {
        db.execute("DROP TABLE IF EXISTS auth_session_tokens");
        db.execute("DROP TABLE IF EXISTS auth_session_families");
        db.execute("DROP TABLE IF EXISTS users");
        db.execute("CREATE TABLE users(id UUID PRIMARY KEY)");
        db.execute("CREATE TABLE auth_session_families(id UUID PRIMARY KEY,user_id UUID,created_at TIMESTAMP WITH TIME ZONE,expires_at TIMESTAMP WITH TIME ZONE,last_activity_at TIMESTAMP WITH TIME ZONE,revoked BOOLEAN DEFAULT FALSE)");
        db.execute("CREATE TABLE auth_session_tokens(refresh_hash VARCHAR(64) PRIMARY KEY,access_hash VARCHAR(64) UNIQUE,family_id UUID REFERENCES auth_session_families(id) ON DELETE CASCADE,access_expires_at TIMESTAMP WITH TIME ZONE,used BOOLEAN DEFAULT FALSE)");
        reset(users);
        user = new User(); user.setId(UUID.randomUUID()); user.setStatus(UserStatus.ACTIVE); user.setEmailVerified(true);
        db.update("INSERT INTO users(id) VALUES (?)", user.getId());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test void tokensAreOpaqueAndOnlyHashesAreStored() {
        var tokens = sessions.create(user.getId());
        assertEquals(43, tokens.access().length());
        assertNotEquals(tokens.access(), tokens.refresh());
        assertEquals(SessionService.hash(tokens.refresh()), db.queryForObject("SELECT refresh_hash FROM auth_session_tokens", String.class));
        assertSame(user, sessions.authenticate(tokens.access()));
        assertNull(sessions.authenticate(tokens.refresh()));
        assertFalse(tokens.toString().contains(tokens.access()));
    }

    @Test void reuseCommitsFamilyRevocationIncludingNewestAccess() {
        var original = sessions.create(user.getId());
        var next = sessions.rotate(original.refresh());
        assertNotNull(next);
        assertSame(user, sessions.authenticate(next.access()));
        assertNull(sessions.rotate(original.refresh()));
        assertNull(sessions.authenticate(next.access()));
        assertNull(sessions.rotate(next.refresh()));
        assertTrue(db.queryForObject("SELECT revoked FROM auth_session_families", Boolean.class));
    }

    @Test void concurrentReuseCannotProduceTwoValidFamilies() throws Exception {
        var original = sessions.create(user.getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<SessionService.Tokens> rotate = () -> { start.await(); return sessions.rotate(original.refresh()); };
            var first = executor.submit(rotate); var second = executor.submit(rotate); start.countDown();
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null));
            assertNull(sessions.authenticate(a != null ? a.access() : b.access()));
        }
    }

    @Test void logoutUsingRefreshRevokesEvenWhenAccessExpired() {
        var tokens = sessions.create(user.getId());
        db.update("UPDATE auth_session_tokens SET access_expires_at=?", OffsetDateTime.now().minusMinutes(1));
        assertNull(sessions.authenticate(tokens.access()));
        sessions.revoke(tokens.refresh());
        assertNull(sessions.rotate(tokens.refresh()));
    }

    @Test void accessExpiresButRefreshWorksWithinAbsoluteLifetime() {
        var tokens = sessions.create(user.getId());
        db.update("UPDATE auth_session_tokens SET access_expires_at=?", OffsetDateTime.now().minusMinutes(1));
        assertNull(sessions.authenticate(tokens.access()));
        var next = sessions.rotate(tokens.refresh());
        assertNotNull(next);
        assertTrue(next.refreshSeconds() <= SessionService.REFRESH_SECONDS);
        db.update("UPDATE auth_session_families SET expires_at=?", OffsetDateTime.now().minusMinutes(1));
        assertNull(sessions.rotate(next.refresh()));
        assertNull(sessions.authenticate(next.access()));
        sessions.cleanup();
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM auth_session_tokens", Integer.class));
    }

    @Test void passwordChangeAndSuspensionInvalidateSessions() {
        var tokens = sessions.create(user.getId());
        user.setPasswordChangedAt(OffsetDateTime.now().plusSeconds(1));
        assertNull(sessions.authenticate(tokens.access()));
        assertNull(sessions.rotate(tokens.refresh()));
        user.setPasswordChangedAt(null);
        var other = sessions.create(user.getId());
        user.setStatus(UserStatus.SUSPENDED);
        assertNull(sessions.authenticate(other.access()));
        assertNull(sessions.rotate(other.refresh()));
    }

    @Test void aNewLoginInvalidatesThePreviousAccessAndRefresh() {
        var first = sessions.create(user.getId());
        var second = sessions.create(user.getId());
        assertNull(sessions.authenticate(first.access()));
        assertNull(sessions.rotate(first.refresh()));
        assertSame(user, sessions.authenticate(second.access()));
        assertNotNull(sessions.rotate(second.refresh()));
    }

    @Test void idleSessionsCannotAuthenticateRefreshOrBeRevived() {
        var tokens = sessions.create(user.getId());
        db.update("UPDATE auth_session_families SET last_activity_at=?", OffsetDateTime.now().minusMinutes(15));
        assertNull(sessions.authenticate(tokens.access()));
        assertNull(sessions.rotate(tokens.refresh()));
        assertNull(sessions.activity(tokens.access(), 0));
        assertNull(sessions.status(tokens.access()));
    }

    @Test void pollingAndRotationDoNotResetIdleDeadline() {
        var tokens = sessions.create(user.getId());
        var last = OffsetDateTime.now().minusMinutes(9).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        db.update("UPDATE auth_session_families SET last_activity_at=?", last);
        sessions.authenticate(tokens.access());
        sessions.status(tokens.access());
        sessions.rotate(tokens.refresh());
        assertEquals(last.toInstant(), db.queryForObject("SELECT last_activity_at FROM auth_session_families",
                OffsetDateTime.class).toInstant());
    }

    @Test void recentInteractionUpdatesOnlyItsOwnFamilyAndNeverMovesTimeBackwards() {
        var tokens = sessions.create(user.getId());
        db.update("UPDATE auth_session_families SET last_activity_at=?", OffsetDateTime.now().minusMinutes(10));
        var status = sessions.activity(tokens.access(), 20000);
        assertNotNull(status);
        assertTrue(status.idleRemainingMillis() <= 880100);
        assertTrue(status.idleRemainingMillis() > 879000);
        assertNull(sessions.activity(tokens.access(), 60001));
        assertNull(sessions.activity(tokens.access(), -1));
        assertNull(sessions.activity("invalid", 0));
        var after = sessions.activity(tokens.access(), 50000);
        assertTrue(after.idleRemainingMillis() > 879000);
    }

    @Test void lateLogoutCannotRevokeANewerSession() {
        var first = sessions.create(user.getId());
        var firstId = sessions.status(first.access()).sessionId();
        var second = sessions.create(user.getId());
        assertFalse(sessions.revokeExpected(second.access(), firstId));
        assertFalse(sessions.revokeExpected(second.refresh(), firstId));
        assertSame(user, sessions.authenticate(second.access()));
        assertTrue(sessions.revokeExpected(first.refresh(), firstId));
        assertSame(user, sessions.authenticate(second.access()));
    }

    @Test void simultaneousLoginsLeaveOnlyOneValidSession() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<SessionService.Tokens> login = () -> { start.await(); return sessions.create(user.getId()); };
            var a = executor.submit(login); var b = executor.submit(login); start.countDown();
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertTrue((sessions.authenticate(first.access()) == null) != (sessions.authenticate(second.access()) == null));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM auth_session_families WHERE revoked=false", Integer.class));
        }
    }
}
