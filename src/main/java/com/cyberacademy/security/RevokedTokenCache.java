package com.cyberacademy.security;

import com.cyberacademy.domains.auth.repository.RevokedTokenRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cache en memoria del check "¿este token está revocado?" que el filtro JWT
 * hace en cada petición autenticada.
 *
 * Sin cache, cada petición ejecuta un SELECT contra revoked_tokens; al cachear
 * el resultado (positivo o negativo) se elimina esa consulta del hot path.
 *
 * Ventana de desincronización: al revocar un token se invalida su entrada,
 * así que un logout toma efecto de inmediato en la misma instancia. Si algún
 * día se escala a varias instancias, una revocación podría tardar hasta el TTL
 * en propagarse; con el tiempo de vida recomendado (≤30 s) es aceptable porque
 * el token está revocado en la BD y cualquier fallo de cache expira pronto.
 */
@Component
public class RevokedTokenCache {

    private final RevokedTokenRepository repository;
    /** TTL corto: una revocación nunca debe quedar más de esto sin surtir efecto. */
    private final Cache<String, Boolean> cache;

    public RevokedTokenCache(RevokedTokenRepository repository,
                             @Value("${app.revoked-token-cache-seconds:30}") long ttlSeconds) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, ttlSeconds)))
                .maximumSize(50_000)
                .build();
    }

    /** true si el jti está revocado (cache 1ra línea), 1 SELECT si falta. */
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(cache.get(jti, key -> repository.existsByJti(key)));
    }

    /** Invalida al revocar un token para que el logout surta efecto de inmediato. */
    public void invalidate(String jti) {
        cache.invalidate(jti);
    }
}
