package com.cyberacademy.domains.messaging;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class MessageRepository {
    public record Item(long id, String senderName, String body, OffsetDateTime createdAt, OffsetDateTime readAt) {}
    public record Sent(long id, int recipientCount, String hash) {}
    private final NamedParameterJdbcTemplate jdbc;
    private static final String ELIGIBLE = "role = 'STUDENT' AND status = 'ACTIVE' AND email_verified = true";

    public MessageRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Sent> previous(UUID sender, UUID requestId) {
        return jdbc.query("SELECT id, recipient_count, request_hash FROM admin_messages WHERE sender_id=:sender AND request_id=:request",
                Map.of("sender", sender, "request", requestId),
                (rs, n) -> new Sent(rs.getLong(1), rs.getInt(2), rs.getString(3))).stream().findFirst();
    }
    public long eligibleCount(List<UUID> ids) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE " + ELIGIBLE + " AND id IN (:ids)",
                Map.of("ids", ids), Long.class);
    }
    public Optional<Long> create(UUID sender, String name, String body, UUID request, String hash) {
        return jdbc.query("""
            INSERT INTO admin_messages(sender_id, sender_name, body, request_id, request_hash)
            VALUES (:sender, :name, :body, :request, :hash)
            ON CONFLICT (sender_id, request_id) DO NOTHING RETURNING id
            """, Map.of("sender", sender, "name", name, "body", body, "request", request, "hash", hash),
                (rs, n) -> rs.getLong(1)).stream().findFirst();
    }
    public int deliver(long id, boolean all, List<UUID> ids) {
        return jdbc.update("INSERT INTO student_messages(student_id, message_id) SELECT id, :message FROM users WHERE "
                        + ELIGIBLE + (all ? "" : " AND id IN (:ids)"),
                Map.of("message", id, "ids", ids));
    }
    public void recordCount(long id, int count) {
        jdbc.update("UPDATE admin_messages SET recipient_count=:count WHERE id=:id", Map.of("count", count, "id", id));
    }
    public List<Item> inbox(UUID student, Long before, int limit) {
        var params = new HashMap<String, Object>();
        params.put("student", student); params.put("limit", limit);
        if (before != null) params.put("before", before);
        return jdbc.query("""
            SELECT m.id, m.sender_name, m.body, m.created_at, s.read_at
            FROM student_messages s JOIN admin_messages m ON m.id = s.message_id
            WHERE s.student_id=:student
            """ + (before == null ? "" : " AND m.id < :before ") + " ORDER BY m.id DESC LIMIT :limit",
                params, (rs, n) -> item(rs));
    }
    public long unread(UUID student) {
        return jdbc.queryForObject("SELECT count(*) FROM student_messages WHERE student_id=:student AND read_at IS NULL",
                Map.of("student", student), Long.class);
    }
    /** One atomic claim, safe across tabs and backend instances. Caller owns the transaction. */
    public List<Item> claim(UUID student) {
        return jdbc.query("""
            WITH pending AS (
                SELECT message_id FROM student_messages
                WHERE student_id=:student AND notified_at IS NULL AND read_at IS NULL
                ORDER BY message_id LIMIT 10 FOR UPDATE SKIP LOCKED
            ), claimed AS (
                UPDATE student_messages s SET notified_at=now()
                FROM pending p WHERE s.student_id=:student AND s.message_id=p.message_id
                RETURNING s.message_id, s.read_at
            )
            SELECT m.id, m.sender_name, m.body, m.created_at, c.read_at
            FROM claimed c JOIN admin_messages m ON m.id=c.message_id ORDER BY m.id
            """, Map.of("student", student), (rs, n) -> item(rs));
    }
    public int markRead(UUID student, long id) {
        return jdbc.update("""
            UPDATE student_messages SET read_at=COALESCE(read_at, now()), notified_at=COALESCE(notified_at, now())
            WHERE student_id=:student AND message_id=:id
            """, Map.of("student", student, "id", id));
    }
    public int delete(UUID student, long id) {
        return jdbc.update("DELETE FROM student_messages WHERE student_id=:student AND message_id=:id",
                Map.of("student", student, "id", id));
    }
    public void clear(UUID student) {
        jdbc.update("DELETE FROM student_messages WHERE student_id=:student", Map.of("student", student));
    }
    private static Item item(ResultSet rs) throws SQLException {
        return new Item(rs.getLong("id"), rs.getString("sender_name"), rs.getString("body"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("read_at", OffsetDateTime.class));
    }
}
