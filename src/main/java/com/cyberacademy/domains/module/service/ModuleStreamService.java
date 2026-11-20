package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.auth.model.UserRole;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.domains.module.dto.ModuleCreatedEvent;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import com.cyberacademy.security.SessionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ModuleStreamService {
    private static final Logger log = LoggerFactory.getLogger(ModuleStreamService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_PER_USER = 2;
    /**
     * Con 512 MB de RAM, 200 emisores abiertos ya es un límite realista.
     * Antes eran 1000: una promesa que la instancia no puede cumplir y que,
     * al superarse, degrada a todos los usuarios a la vez.
     */
    private static final int MAX_CONNECTIONS = 200;
    /**
     * Cloudflare corta una conexión ociosa a los 100 s, así que 30 s deja margen de sobra.
     * Antes eran 25 s: un 20 % más de consultas a la base sin ninguna ventaja.
     */
    private static final long HEARTBEAT_SECONDS = 30;
    /** Dos hilos para que una consulta lenta a Supabase no retrase los heartbeats del resto. */
    private static final int HEARTBEAT_WORKERS = 2;

    private final ConcurrentHashMap<SseEmitter, Connection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler;
    private final ExecutorService heartbeatWorkers;
    /** Evita que dos rondas de heartbeat se solapen si la base de datos va lenta. */
    private final AtomicBoolean heartbeatInProgress = new AtomicBoolean(false);
    private final UserRepository users;
    private final CourseModuleRepository modules;
    private final SessionService sessions;
    private final TransactionTemplate readNewTransaction;

    record Connection(UUID userId, String jti, Instant expiresAt, Instant openedAt) {}

    public ModuleStreamService(UserRepository users, CourseModuleRepository modules, SessionService sessions,
                               PlatformTransactionManager transactionManager) {
        this.users = users;
        this.modules = modules;
        this.sessions = sessions;
        this.readNewTransaction = new TransactionTemplate(transactionManager);
        this.readNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readNewTransaction.setReadOnly(true);
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeatWorkers = Executors.newFixedThreadPool(HEARTBEAT_WORKERS, runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat-worker");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatScheduler.scheduleWithFixedDelay(
                this::sendHeartbeats, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public SseEmitter connect(String token) {
        var user = sessions.authenticate(token);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        UUID userId = user.getId();
        Connection connection = new Connection(userId, token, Instant.now().plusSeconds(SessionService.ACCESS_SECONDS), Instant.now());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> replaced = new ArrayList<>();
        synchronized (connections) {
            // Al recargar o cambiar de página, el servidor no sabe que la conexión anterior
            // murió hasta el siguiente heartbeat. Si el usuario llega al límite se cierran sus
            // conexiones más antiguas en lugar de rechazar la nueva: el límite sigue siendo el mismo.
            List<SseEmitter> mine = connections.entrySet().stream()
                    .filter(e -> e.getValue().userId().equals(userId))
                    .sorted(Comparator.comparing(e -> e.getValue().openedAt()))
                    .map(Map.Entry::getKey)
                    .toList();
            replaced.addAll(mine.subList(0, Math.max(0, mine.size() - MAX_PER_USER + 1)));
            replaced.forEach(connections::remove);
            if (connections.size() >= MAX_CONNECTIONS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Límite de conexiones alcanzado");
            }
            connections.put(emitter, connection);
        }
        replaced.forEach(this::close);
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
            } catch (IOException ex) {
                connections.remove(emitter); // cliente desconectado, ver sendHeartbeats
            } catch (RuntimeException ex) {
                close(emitter);
            }
        });
    }

    /**
     * Sesión válida para el stream: token vivo, no revocado y usuario ACTIVE, verificado y STUDENT.
     * Usa la misma consulta que el filtro JWT (usuario + revocación en un solo round-trip);
     * antes eran dos consultas por conexión y por heartbeat.
     */
    boolean authorized(Connection connection) {
        if (connection.jti() == null || !connection.expiresAt().isAfter(Instant.now())) {
            return false;
        }
        var user = sessions.authenticate(connection.jti());
        return user != null && user.getId().equals(connection.userId()) && user.getRole() == UserRole.STUDENT
                && user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified();
    }

    /** Un tick cada 30 s. Los checks se reparten entre 2 workers y nunca se solapan entre ticks. */
    private void sendHeartbeats() {
        if (!heartbeatInProgress.compareAndSet(false, true)) {
            return; // la ronda anterior sigue viva: no acumulamos trabajo
        }
        List<Map.Entry<SseEmitter, Connection>> snapshot = new ArrayList<>(connections.entrySet());
        if (snapshot.isEmpty()) {
            heartbeatInProgress.set(false);
            return;
        }
        AtomicInteger pending = new AtomicInteger(snapshot.size());
        for (Map.Entry<SseEmitter, Connection> entry : snapshot) {
            Runnable task = () -> {
                try {
                    heartbeat(entry.getKey(), entry.getValue());
                } finally {
                    if (pending.decrementAndGet() == 0) {
                        heartbeatInProgress.set(false);
                    }
                }
            };
            try {
                heartbeatWorkers.execute(task);
            } catch (RejectedExecutionException ex) {
                // Apagado en curso: liberamos la ronda para no dejar el flag bloqueado.
                if (pending.decrementAndGet() == 0) {
                    heartbeatInProgress.set(false);
                }
            }
        }
    }

    private void heartbeat(SseEmitter emitter, Connection connection) {
        try {
            if (!authorized(connection)) { close(emitter); return; }
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException ex) {
            // El cliente cerró la pestaña: el contenedor ya da la respuesta por terminada.
            // Llamar a complete() aquí intentaría escribir en ella y generaría otro error.
            connections.remove(emitter);
        } catch (RuntimeException ex) {
            close(emitter);
        }
    }

    private void close(SseEmitter emitter) {
        connections.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }

    /**
     * Se publica antes de que arranque el graceful shutdown, así que cerrar los SSE aquí
     * permite que el apagado termine de inmediato en lugar de esperar el timeout.
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        connections.keySet().forEach(this::close);
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
        heartbeatWorkers.shutdownNow();
        connections.keySet().forEach(this::close);
    }
}
