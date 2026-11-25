package com.cyberacademy.security;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.auth.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Solo hashes SHA-256 de secretos aleatorios de 256 bits llegan a PostgreSQL. */
@Service
public class SessionService {
    public static final long ACCESS_SECONDS = 600;
    public static final long REFRESH_SECONDS = 7 * 24 * 3600;
    public static final long IDLE_SECONDS = 15 * 60;
    private final JdbcTemplate db;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();

    public SessionService(JdbcTemplate db, UserRepository users) {
        this.db = db;
        this.users = users;
    }

    // Nunca serializar este objeto en una respuesta HTTP ni escribirlo en logs.
    public record Tokens(String access, String refresh, long refreshSeconds) {
        @Override public String toString() { return "Tokens[REDACTED]"; }
    }
    private record Family(UUID id, UUID userId, OffsetDateTime created, OffsetDateTime expires,
                          OffsetDateTime lastActivity, boolean revoked) {}
    // Identificador público de correlación, nunca una credencial de acceso.
    public record Status(UUID sessionId, UUID userId, long idleRemainingMillis) {}

    @Transactional
    public Tokens create(UUID userId) {
        // Serializar logins, incluso si aún no existe ninguna familia.
        db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, userId);
        var now = OffsetDateTime.now();
        UUID id = UUID.randomUUID();
        db.update("UPDATE auth_session_families SET revoked=true WHERE user_id=? AND revoked=false", userId);
        db.update("INSERT INTO auth_session_families(id,user_id,created_at,expires_at,last_activity_at) VALUES (?,?,?,?,?)",
                id, userId, now, now.plusSeconds(REFRESH_SECONDS), now);
        return issue(id, REFRESH_SECONDS);
    }

    @Transactional
    public Tokens rotate(String refresh) {
        if (!validFormat(refresh)) return null;
        var ids = db.queryForList("SELECT family_id FROM auth_session_tokens WHERE refresh_hash=?", UUID.class, hash(refresh));
        if (ids.isEmpty()) return null;
        // Todas las rotaciones y revocaciones de una familia usan la misma cerradura.
        var families = db.query("SELECT * FROM auth_session_families WHERE id=? FOR UPDATE", (rs, n) ->
                new Family(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("last_activity_at", OffsetDateTime.class),
                        rs.getBoolean("revoked")), ids.getFirst());
        if (families.isEmpty()) return null;
        Family family = families.getFirst();
        if (family.revoked || !family.expires.isAfter(OffsetDateTime.now())
                || !family.lastActivity.isAfter(OffsetDateTime.now().minusSeconds(IDLE_SECONDS))) return null;
        boolean used = Boolean.TRUE.equals(db.queryForObject(
                "SELECT used FROM auth_session_tokens WHERE refresh_hash=?", Boolean.class, hash(refresh)));
        User user = users.findById(family.userId).orElse(null);
        if (used || !eligible(user, family.created)) {
            db.update("UPDATE auth_session_families SET revoked=true WHERE id=?", family.id);
            // Retornar, no lanzar: la revocación debe confirmarse incluso ante reuso.
            return null;
        }
        db.update("UPDATE auth_session_tokens SET used=true WHERE refresh_hash=?", hash(refresh));
        long remaining = Math.max(0, java.time.Duration.between(OffsetDateTime.now(), family.expires).toSeconds());
        return issue(family.id, remaining);
    }

    @Transactional(readOnly = true)
    public User authenticate(String access) {
        if (!validFormat(access)) return null;
        var families = db.query("""
                SELECT f.* FROM auth_session_families f JOIN auth_session_tokens t ON t.family_id=f.id
                WHERE t.access_hash=? AND f.revoked=false AND f.expires_at>? AND t.access_expires_at>?
                AND f.last_activity_at>?
                """, (rs, n) -> new Family(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("last_activity_at", OffsetDateTime.class),
                rs.getBoolean("revoked")), hash(access), OffsetDateTime.now(), OffsetDateTime.now(),
                OffsetDateTime.now().minusSeconds(IDLE_SECONDS));
        if (families.isEmpty()) return null;
        Family family = families.getFirst();
        User user = users.findById(family.userId).orElse(null);
        return eligible(user, family.created) ? user : null;
    }

    @Transactional
    public void revoke(String token) {
        if (!validFormat(token)) return;
        // Mantener tokens usados hasta vencer la familia permite detectar reuso.
        db.update("""
                UPDATE auth_session_families SET revoked=true WHERE id IN
                (SELECT family_id FROM auth_session_tokens WHERE access_hash=? OR refresh_hash=?)
                """, hash(token), hash(token));
    }

    @Transactional(readOnly = true)
    public Status status(String access) {
        if (!validFormat(access)) return null;
        var now = OffsetDateTime.now();
        var rows = db.query("""
                SELECT f.id, f.user_id, f.last_activity_at FROM auth_session_families f
                JOIN auth_session_tokens t ON t.family_id=f.id
                WHERE t.access_hash=? AND f.revoked=false AND f.expires_at>?
                AND t.access_expires_at>? AND f.last_activity_at>?
                """, (rs, n) -> new Status(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                Math.max(0, java.time.Duration.between(now,
                        rs.getObject("last_activity_at", OffsetDateTime.class).plusSeconds(IDLE_SECONDS)).toMillis())),
                hash(access), now, now, now.minusSeconds(IDLE_SECONDS));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional
    public Status activity(String access, long ageMillis) {
        if (!validFormat(access) || ageMillis < 0 || ageMillis > 60000) return null;
        var now = OffsetDateTime.now();
        // Solo este endpoint renueva actividad. GETs, polling y refresh NO lo hacen.
        int changed = db.update("""
                UPDATE auth_session_families SET last_activity_at=GREATEST(last_activity_at, ?)
                WHERE revoked=false AND expires_at>? AND last_activity_at>?
                AND id IN (SELECT family_id FROM auth_session_tokens
                           WHERE access_hash=? AND access_expires_at>?)
                """, now.minusNanos(ageMillis * 1000000), now, now.minusSeconds(IDLE_SECONDS), hash(access), now);
        return changed == 0 ? null : status(access);
    }

    /** Evita que un cierre tardío de una pestaña antigua cierre una sesión recién creada. */
    @Transactional
    public boolean revokeExpected(String token, UUID sessionId) {
        if (!validFormat(token) || sessionId == null) return false;
        return db.update("""
                UPDATE auth_session_families SET revoked=true WHERE id=? AND id IN
                (SELECT family_id FROM auth_session_tokens WHERE access_hash=? OR refresh_hash=?)
                """, sessionId, hash(token), hash(token)) > 0;
    }

    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanup() {
        db.update("DELETE FROM auth_session_families WHERE expires_at < ?", OffsetDateTime.now());
    }

    private boolean eligible(User user, OffsetDateTime created) {
        return user != null && user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified()
                && (user.getPasswordChangedAt() == null || created.isAfter(user.getPasswordChangedAt()));
    }

    private Tokens issue(UUID family, long remaining) {
        String access = randomToken(), refresh = randomToken();
        db.update("INSERT INTO auth_session_tokens(refresh_hash,access_hash,family_id,access_expires_at) VALUES (?,?,?,?)",
                hash(refresh), hash(access), family, OffsetDateTime.now().plusSeconds(Math.min(ACCESS_SECONDS, remaining)));
        return new Tokens(access, refresh, remaining);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean validFormat(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
