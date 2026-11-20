package com.cyberacademy.domains.profile.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ProfilePhotoRepository {
    public record Identity(String avatar, String photoUrl) {}
    public record Photo(UUID userId, byte[] image) {}
    private final NamedParameterJdbcTemplate jdbc;

    public ProfilePhotoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<UUID, Identity> identities(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<UUID, Identity>();
        jdbc.query("""
            SELECT u.id, u.avatar, p.version FROM users u
            LEFT JOIN profile_photos p ON p.user_id = u.id WHERE u.id IN (:ids)
            """, Map.of("ids", ids), rs -> {
            UUID version = rs.getObject("version", UUID.class);
            result.put(rs.getObject("id", UUID.class), new Identity(rs.getString("avatar"),
                    version == null ? null : "/profile/photos/" + version));
        });
        return result;
    }

    public String url(UUID userId) {
        var identity = identities(List.of(userId)).get(userId);
        return identity == null ? null : identity.photoUrl();
    }

    public Optional<Photo> find(UUID version) {
        return jdbc.query("SELECT user_id, image FROM profile_photos WHERE version = :version",
                Map.of("version", version), (rs, n) ->
                        new Photo(rs.getObject("user_id", UUID.class), rs.getBytes("image"))).stream().findFirst();
    }

    public void save(UUID userId, byte[] image) {
        jdbc.update("""
            INSERT INTO profile_photos(user_id, version, image) VALUES (:id, :version, :image)
            ON CONFLICT (user_id) DO UPDATE SET version = EXCLUDED.version, image = EXCLUDED.image
            """, Map.of("id", userId, "version", UUID.randomUUID(), "image", image));
    }

    public void delete(UUID userId) {
        jdbc.update("DELETE FROM profile_photos WHERE user_id = :id", Map.of("id", userId));
    }
}
