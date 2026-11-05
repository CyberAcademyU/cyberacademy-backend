package com.cyberacademy.domains.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.cyberacademy.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Limitador local por correo y por dirección de conexión. */
@Service
public class RateLimitService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_IP_ATTEMPTS = 50;
    private static final long WINDOW_MS = Duration.ofMinutes(15).toMillis();

    // Cachés separadas impiden que correos arbitrarios desplacen los límites por IP.
    private final Cache<String, RateLimitRecord> emails = newCache();
    private final Cache<String, RateLimitRecord> origins = newCache();
    private final LongSupplier clock;

    public RateLimitService() {
        this(System::currentTimeMillis);
    }

    RateLimitService(LongSupplier clock) {
        this.clock = clock;
    }

    private static Cache<String, RateLimitRecord> newCache() {
        return Caffeine.newBuilder().maximumSize(100_000)
                .expireAfterAccess(Duration.ofMinutes(15)).build();
    }

    public void check(String key) {
        var attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest() : null;
        String origin = request == null ? "unknown" : request.getRemoteAddr();
        String operation = key.substring(0, key.indexOf(':') + 1);
        // Se cuentan también las solicitudes rechazadas por el límite de correo.
        long now = clock.getAsLong();
        long ipWait = consume(origins, operation + origin, MAX_IP_ATTEMPTS, now);
        long emailWait = consume(emails, key, MAX_ATTEMPTS, now);
        long waitMs = Math.max(ipWait, emailWait);
        if (waitMs > 0) throw new RateLimitException((waitMs + 999) / 1000);
    }

    private long consume(Cache<String, RateLimitRecord> cache, String key, int limit, long now) {
        AtomicLong waitMs = new AtomicLong();
        cache.asMap().compute(key, (ignored, old) -> {
            RateLimitRecord record = old == null || now - old.firstAttempt() >= WINDOW_MS
                    ? new RateLimitRecord(now, 0) : old;
            if (record.count() >= limit) {
                waitMs.set(WINDOW_MS - (now - record.firstAttempt()));
                return record;
            }
            return new RateLimitRecord(record.firstAttempt(), record.count() + 1);
        });
        return waitMs.get();
    }

    private record RateLimitRecord(long firstAttempt, int count) {}
}
