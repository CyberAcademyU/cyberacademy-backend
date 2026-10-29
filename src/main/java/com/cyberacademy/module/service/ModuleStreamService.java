package com.cyberacademy.module.service;

import com.cyberacademy.auth.entity.UserRole;
import com.cyberacademy.auth.entity.UserStatus;
import com.cyberacademy.auth.repository.RevokedTokenRepository;
import com.cyberacademy.auth.repository.UserRepository;
import com.cyberacademy.module.dto.ModuleCreatedEvent;
import com.cyberacademy.module.repository.CourseModuleRepository;
import com.cyberacademy.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ModuleStreamService {
    private static final Logger log = LoggerFactory.getLogger(ModuleStreamService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_PER_USER = 2;
    private static final int MAX_CONNECTIONS = 1000;
    private final ConcurrentHashMap<SseEmitter, Connection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final UserRepository users;
    private final RevokedTokenRepository revokedTokens;
    private final CourseModuleRepository modules;
    private final JwtService jwtService;
    private final TransactionTemplate readNewTransaction;

    private record Connection(UUID userId, String jti, Instant expiresAt) {}

    public ModuleStreamService(UserRepository users, RevokedTokenRepository revokedTokens,
                               CourseModuleRepository modules, JwtService jwtService,
                               PlatformTransactionManager transactionManager) {
        this.users = users;
        this.revokedTokens = revokedTokens;
        this.modules = modules;
        this.jwtService = jwtService;
        this.readNewTransaction = new TransactionTemplate(transactionManager);
        this.readNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readNewTransaction.setReadOnly(true);
        heartbeat.scheduleWithFixedDelay(this::sendHeartbeats, 25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter connect(String token) {
        Claims claims = jwtService.getClaimsFromToken(token);
        UUID userId = UUID.fromString(claims.getSubject());
        Connection connection = new Connection(userId, claims.getId(), claims.getExpiration().toInstant());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        synchronized (connections) {
            long userConnections = connections.values().stream().filter(c -> c.userId().equals(userId)).count();
            if (connections.size() >= MAX_CONNECTIONS || userConnections >= MAX_PER_USER) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Límite de conexiones alcanzado");
            }
            connections.put(emitter, connection);
        }
        emitter.onCompletion(() -> connections.remove(emitter));
        emitter.onTimeout(() -> { connections.remove(emitter); emitter.complete(); });
        emitter.onError(error -> connections.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException ex) {
            close(emitter);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void moduleCreated(ModuleCreatedEvent event) {
        // Leer en una transacción nueva evita reutilizar el contexto ya confirmado.
        ModuleCreatedEvent visible;
        try {
            visible = readNewTransaction.execute(status -> modules.findById(event.id())
                    .filter(module -> module.isActive())
                    .map(module -> new ModuleCreatedEvent(module.getId(), module.getTitle()))
                    .orElse(null));
        } catch (RuntimeException ex) {
            log.warn("No se pudo publicar el evento de módulo creado", ex);
            return;
        }
        if (visible == null) return;
        connections.forEach((emitter, connection) -> {
            try {
                if (!authorized(connection)) { close(emitter); return; }
                emitter.send(SseEmitter.event().name("module-created").data(visible, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                close(emitter);
            } catch (RuntimeException ex) {
                close(emitter);
            }
        });
    }

    private boolean authorized(Connection connection) {
        return connection.jti() != null && connection.expiresAt().isAfter(Instant.now())
                && !revokedTokens.existsByJti(connection.jti())
                && users.findById(connection.userId()).filter(user -> user.getStatus() == UserStatus.ACTIVE
                    && user.isEmailVerified() && user.getRole() == UserRole.STUDENT).isPresent();
    }

    private void sendHeartbeats() {
        connections.forEach((emitter, connection) -> {
            try {
                if (!authorized(connection)) { close(emitter); return; }
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | RuntimeException ex) {
                close(emitter);
            }
        });
    }

    private void close(SseEmitter emitter) {
        connections.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
        connections.keySet().forEach(this::close);
    }
}
