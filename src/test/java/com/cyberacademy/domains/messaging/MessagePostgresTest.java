package com.cyberacademy.domains.messaging;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.security.CurrentUserProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL: validates migration, delivery, IDOR, rollback and atomic claims. */
@EnabledIfEnvironmentVariable(named="MESSAGING_TEST_DATABASE_URL", matches=".+")
class MessagePostgresTest {
    private JdbcTemplate jdbc;
    private MessageRepository repository;
    private TransactionTemplate tx;
    private final UUID admin = UUID.randomUUID(), student = UUID.randomUUID(), other = UUID.randomUUID();

    @BeforeEach void setup() {
        String url = System.getenv("MESSAGING_TEST_DATABASE_URL");
        var target = java.net.URI.create(url.substring("jdbc:".length()));
        if (!Set.of("localhost", "127.0.0.1").contains(target.getHost()) || !"/messaging_test".equals(target.getPath()))
            throw new IllegalStateException("Esta prueba solo puede usar messaging_test en localhost.");
        var source = new DriverManagerDataSource(url, "postgres", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS student_messages, admin_messages, users CASCADE");
        jdbc.execute("""
            CREATE TABLE users(id UUID PRIMARY KEY, role VARCHAR(20), status VARCHAR(30), email_verified BOOLEAN)
            """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V14__student_messages.sql")).execute(source);
        repository = new MessageRepository(new NamedParameterJdbcTemplate(source));
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO users VALUES (?, 'ADMIN', 'ACTIVE', true), (?, 'STUDENT', 'ACTIVE', true), (?, 'STUDENT', 'ACTIVE', true)", admin, student, other);
    }
    private MessageService service() {
        var current = mock(CurrentUserProvider.class);
        var user = new User(); user.setId(admin); user.setRole(UserRole.ADMIN);
        user.setFirstName("Admin"); user.setLastName("Ejemplo");
        when(current.requireCurrentUser()).thenReturn(user);
        return new MessageService(repository, current, new RateLimitService());
    }

    @Test void allNeverIncludesAdminSuspendedOrUnverifiedUsersAndDeletesAreIsolated() {
        jdbc.update("INSERT INTO users VALUES (?, 'STUDENT', 'SUSPENDED', true), (?, 'STUDENT', 'ACTIVE', false)", UUID.randomUUID(), UUID.randomUUID());
        var service = service();
        var result = tx.execute(status -> service.send(new MessageService.SendRequest(MessageService.Audience.ALL, List.of(), "Mensaje", UUID.randomUUID())));
        assertEquals(2, result.recipientCount());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM student_messages WHERE student_id=?", Integer.class, admin));
        assertEquals(1, repository.inbox(student, null, 21).size());
        assertEquals(0, repository.delete(UUID.randomUUID(), result.id()));
        repository.clear(student);
        assertTrue(repository.inbox(student, null, 21).isEmpty());
        assertEquals(1, repository.inbox(other, null, 21).size());
        assertEquals(0, repository.markRead(student, result.id()));
    }

    @Test void concurrentRetriesWithSameRequestIdProduceOnlyOneMessage() throws Exception {
        var service = service();
        var request = new MessageService.SendRequest(MessageService.Audience.SELECTED, List.of(student), "Una vez", UUID.randomUUID());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> tx.execute(status -> service.send(request)));
            var b = executor.submit(() -> tx.execute(status -> service.send(request)));
            assertEquals(a.get().id(), b.get().id());
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM admin_messages", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM student_messages", Integer.class));
    }

    @Test void notificationClaimIsAtomicAndDoesNotMarkMessageRead() throws Exception {
        var service = service();
        tx.execute(status -> service.send(new MessageService.SendRequest(MessageService.Audience.ALL, List.of(), "Aviso", UUID.randomUUID())));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> tx.execute(status -> repository.claim(student)));
            var b = executor.submit(() -> tx.execute(status -> repository.claim(student)));
            assertEquals(1, a.get().size() + b.get().size());
        }
        assertEquals(1, repository.unread(student));
        assertTrue(tx.execute(status -> repository.claim(student)).isEmpty());
        assertEquals(1, tx.execute(status -> repository.claim(other)).size());
    }

    @Test void emptyAudienceRollsBackAndCannotIncludeAdminById() {
        var service = service();
        assertThrows(Exception.class, () -> tx.execute(status -> service.send(new MessageService.SendRequest(
                MessageService.Audience.SELECTED, List.of(admin), "No permitido", UUID.randomUUID()))));
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE role='STUDENT'");
        assertThrows(Exception.class, () -> tx.execute(status -> service.send(new MessageService.SendRequest(
                MessageService.Audience.ALL, List.of(), "Sin destinatarios", UUID.randomUUID()))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM admin_messages", Integer.class));
    }
}
