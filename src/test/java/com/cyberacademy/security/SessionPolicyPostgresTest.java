package com.cyberacademy.security;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Solo la base efímera de CI, nunca una base real de CyberAcademy. */
@EnabledIfEnvironmentVariable(named="MESSAGING_TEST_DATABASE_URL", matches=".+")
class SessionPolicyPostgresTest {
    private JdbcTemplate db;
    private SessionService sessions;
    private TransactionTemplate tx;
    private User user;

    @BeforeEach void setup() {
        String url = System.getenv("MESSAGING_TEST_DATABASE_URL");
        var target = java.net.URI.create(url.substring("jdbc:".length()));
        if (!Set.of("localhost", "127.0.0.1").contains(target.getHost())
                || !"/messaging_test".equals(target.getPath()))
            throw new IllegalStateException("Solo se admite messaging_test en localhost.");
        var source = new DriverManagerDataSource(url, "postgres", "");
        db = new JdbcTemplate(source);
        db.execute("DROP TABLE IF EXISTS auth_session_tokens, auth_session_families");
        // Cada clase de integración de este runner usa una BD local desechable.
        db.execute("DROP TABLE IF EXISTS users CASCADE");
        db.execute("CREATE TABLE users(id UUID PRIMARY KEY)");
        db.execute("""
            DO $$ BEGIN
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF;
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF;
            END $$
            """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__opaque_cookie_sessions.sql")).execute(source);
        user = new User(); user.setId(UUID.randomUUID()); user.setStatus(UserStatus.ACTIVE); user.setEmailVerified(true);
        db.update("INSERT INTO users VALUES (?)", user.getId());
        // Validar también consolidación de sesiones preexistentes en la migración real.
        db.update("""
            INSERT INTO auth_session_families(id,user_id,created_at,expires_at)
            VALUES (?,?,now()-interval '1 hour',now()+interval '1 day'), (?,?,now(),now()+interval '1 day')
            """, UUID.randomUUID(), user.getId(), UUID.randomUUID(), user.getId());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V15__single_session_and_idle_timeout.sql")).execute(source);
        var users = mock(UserRepository.class);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        sessions = new SessionService(db, users);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test void migrationKeepsOnlyLatestAndConcurrentLoginsCannotBypassUniqueIndex() throws Exception {
        assertEquals(1, db.queryForObject("SELECT count(*) FROM auth_session_families WHERE revoked=false", Integer.class));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return tx.execute(s -> sessions.create(user.getId())); });
            var b = executor.submit(() -> { start.await(); return tx.execute(s -> sessions.create(user.getId())); });
            start.countDown();
            var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
            assertTrue((sessions.authenticate(first.access()) == null) != (sessions.authenticate(second.access()) == null));
        }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM auth_session_families WHERE revoked=false", Integer.class));
    }

    @Test void realPostgresIdleBoundaryAndActivityCannotReviveExpiredFamily() {
        var tokens = tx.execute(s -> sessions.create(user.getId()));
        db.update("UPDATE auth_session_families SET last_activity_at=? WHERE revoked=false",
                OffsetDateTime.now().minusMinutes(14));
        assertNotNull(tx.execute(s -> sessions.activity(tokens.access(), 2000)));
        db.update("UPDATE auth_session_families SET last_activity_at=? WHERE revoked=false",
                OffsetDateTime.now().minusMinutes(15));
        assertNull(sessions.authenticate(tokens.access()));
        assertNull(tx.execute(s -> sessions.rotate(tokens.refresh())));
        assertNull(tx.execute(s -> sessions.activity(tokens.access(), 0)));
    }

    @Test void replacedRefreshAndLateLogoutNeverAffectNewSession() {
        var first = tx.execute(s -> sessions.create(user.getId()));
        UUID oldId = sessions.status(first.access()).sessionId();
        var second = tx.execute(s -> sessions.create(user.getId()));
        assertNull(tx.execute(s -> sessions.rotate(first.refresh())));
        assertEquals(Boolean.FALSE, tx.execute(s -> sessions.revokeExpected(second.refresh(), oldId)));
        assertNotNull(sessions.authenticate(second.access()));
    }
}
