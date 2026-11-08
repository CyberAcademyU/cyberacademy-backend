package com.cyberacademy.domains.gamification.service;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.gamification.dto.*;
import com.cyberacademy.domains.gamification.model.BadgeType;
import com.cyberacademy.domains.gamification.model.CompletedSectionRow;
import com.cyberacademy.domains.gamification.model.LeaderboardRow;
import com.cyberacademy.domains.gamification.model.League;
import com.cyberacademy.domains.gamification.model.ModuleResultRow;
import com.cyberacademy.domains.gamification.repository.GamificationRepository;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Clasificación, rachas e insignias.
 *
 * La XP y el puesto los calcula la función fn_leaderboard (db/functions/gamification.sql),
 * que contiene las reglas de XP. Nada de esto se guarda: se deduce del progreso.
 */
@Service
public class GamificationService {

    private static final int LEADERBOARD_SIZE = 50;
    private static final int ACTIVITY_DAYS = 365;
    private static final int STREAK_BADGE_DAYS = 3;
    private static final int MANY_MODULES_BADGE = 5;
    private static final OffsetDateTime BEGINNING = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    public enum Period { WEEK, ALL }

    private final GamificationRepository gamificationRepository;
    private final CourseModuleRepository moduleRepository;
    private final ZoneId zone;
    /**
     * fn_leaderboard calcula el ranking completo de la BD; con esta cache (TTL corto)
     * las lecturas repetidas (perfil, cabecera, panel) reutilizan el mismo cómputo.
     * La XP puede atrasar como máximo el TTL — imperceptible en gamificación.
     */
    private final com.github.benmanes.caffeine.cache.Cache<OffsetDateTime, List<LeaderboardRow>> leaderboardCache;

    public GamificationService(GamificationRepository gamificationRepository,
                               CourseModuleRepository moduleRepository,
                               @Value("${app.time-zone:America/Panama}") String zone,
                               @Value("${app.leaderboard-cache-seconds:20}") long leaderboardTtlSeconds) {
        this.gamificationRepository = gamificationRepository;
        this.moduleRepository = moduleRepository;
        this.zone = ZoneId.of(zone);
        this.leaderboardCache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, leaderboardTtlSeconds)))
                .maximumSize(16)
                .build();
    }

    /** Lista de filas del ranking, cacheada por ventana de tiempo. Inmutable. */
    private List<LeaderboardRow> leaderboardRows(OffsetDateTime since) {
        return List.copyOf(leaderboardCache.get(since, key ->
                List.copyOf(gamificationRepository.leaderboard(key))));
    }

    // =========================================================================
    // Clasificación
    // =========================================================================

    @Transactional(readOnly = true)
    public LeaderboardResponse leaderboard(User me, Period period) {
        List<LeaderboardRow> rows = leaderboardRows(period == Period.WEEK ? startOfWeek() : BEGINNING);

        List<LeaderboardEntryResponse> entries = rows.stream()
                .filter(row -> row.getRanking() != null)
                .limit(LEADERBOARD_SIZE)
                .map(row -> toEntry(row, me))
                .toList();

        LeaderboardEntryResponse mine = rows.stream()
                .filter(row -> row.getUserId().equals(me.getId()))
                .findFirst()
                .map(row -> toEntry(row, me))
                .orElse(null);

        long totalRanked = rows.stream().filter(row -> row.getRanking() != null).count();
        return new LeaderboardResponse(period.name(), entries, mine, totalRanked, !me.isShowInLeaderboard());
    }

    // =========================================================================
    // Estadísticas del perfil
    // =========================================================================

    public record PlayerStats(
            long xp,
            LeagueResponse league,
            Integer rank,
            long totalRanked,
            int modulesCompleted,
            int modulesTotal,
            BigDecimal averageScore,
            int currentStreak,
            int longestStreak,
            List<BadgeResponse> badges,
            List<ActivityDayResponse> activity
    ) {}

    @Transactional(readOnly = true)
    public PlayerStats statsFor(User user) {
        List<LeaderboardRow> rows = leaderboardRows(BEGINNING);
        Optional<LeaderboardRow> mine = rows.stream()
                .filter(row -> row.getUserId().equals(user.getId()))
                .findFirst();
        long xp = mine.map(LeaderboardRow::getTotalXp).orElse(0L);
        Integer rank = mine.map(LeaderboardRow::getRanking).map(Long::intValue).orElse(null);
        long totalRanked = rows.stream().filter(row -> row.getRanking() != null).count();

        List<CompletedSectionRow> sections = gamificationRepository.completedSections(user.getId());
        List<ModuleResultRow> modules = gamificationRepository.moduleResults(user.getId());
        int activeModules = (int) moduleRepository.countByActiveTrue();

        Streak streak = streak(sections);
        List<ModuleResultRow> passed = modules.stream()
                .filter(GamificationService::passed)
                .sorted(Comparator.comparing(ModuleResultRow::getCompletedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new PlayerStats(
                xp,
                LeagueResponse.of(League.forXp(xp)),
                rank,
                totalRanked,
                passed.size(),
                activeModules,
                averageScore(modules),
                streak.current(),
                streak.longest(),
                badges(user, sections, passed, activeModules, streak),
                activity(sections));
    }

    /**
     * Alias si lo tiene; si no, nombre e inicial del apellido.
     * Misma regla que fn_public_name en la BD (se usa para el propio perfil, que no pasa por la clasificación).
     */
    public static String publicName(String displayName, String firstName, String lastName) {
        if (displayName != null && !displayName.isBlank()) return displayName;
        String initial = lastName == null || lastName.isBlank() ? "" : " " + lastName.strip().charAt(0) + ".";
        return (firstName == null ? "" : firstName.strip()) + initial;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private record Streak(int current, int longest, OffsetDateTime reachedBadgeAt) {}

    /** Las funciones devuelven timestamptz, que llega como Instant; los DTO usan OffsetDateTime. */
    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static boolean passed(ModuleResultRow row) {
        return "COMPLETED".equals(row.getStatus());
    }

    private static boolean graded(ModuleResultRow row) {
        return row.getScore() != null && ("COMPLETED".equals(row.getStatus()) || "FAILED".equals(row.getStatus()));
    }

    private LeaderboardEntryResponse toEntry(LeaderboardRow row, User me) {
        return new LeaderboardEntryResponse(
                row.getRanking() == null ? null : row.getRanking().intValue(),
                row.getPublicName(),
                row.getAvatar(),
                row.getXp(),
                row.getModulesCompleted(),
                LeagueResponse.of(League.forXp(row.getTotalXp())),
                row.getUserId().equals(me.getId()));
    }

    private OffsetDateTime startOfWeek() {
        return LocalDate.now(zone).with(DayOfWeek.MONDAY).atStartOfDay(zone).toOffsetDateTime();
    }

    private BigDecimal averageScore(List<ModuleResultRow> modules) {
        List<BigDecimal> scores = modules.stream()
                .filter(GamificationService::graded)
                .map(ModuleResultRow::getScore)
                .toList();
        if (scores.isEmpty()) return null;
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    private Streak streak(List<CompletedSectionRow> sections) {
        TreeMap<LocalDate, OffsetDateTime> firstByDay = new TreeMap<>();
        for (CompletedSectionRow section : sections) {
            if (section.getDoneAt() == null) continue;
            firstByDay.putIfAbsent(section.getDoneAt().atZone(zone).toLocalDate(), toOffset(section.getDoneAt()));
        }

        int run = 0;
        int longest = 0;
        LocalDate previous = null;
        OffsetDateTime reachedAt = null;
        for (Map.Entry<LocalDate, OffsetDateTime> day : firstByDay.entrySet()) {
            run = previous != null && day.getKey().equals(previous.plusDays(1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            if (run == STREAK_BADGE_DAYS && reachedAt == null) reachedAt = day.getValue();
            previous = day.getKey();
        }

        // La racha sigue viva si hubo actividad hoy o ayer
        LocalDate today = LocalDate.now(zone);
        int current = previous != null && !previous.isBefore(today.minusDays(1)) ? run : 0;
        return new Streak(current, longest, reachedAt);
    }

    private List<ActivityDayResponse> activity(List<CompletedSectionRow> sections) {
        LocalDate from = LocalDate.now(zone).minusDays(ACTIVITY_DAYS - 1L);
        TreeMap<LocalDate, Integer> counts = new TreeMap<>();
        for (CompletedSectionRow section : sections) {
            if (section.getDoneAt() == null) continue;
            LocalDate day = section.getDoneAt().atZone(zone).toLocalDate();
            if (!day.isBefore(from)) counts.merge(day, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new ActivityDayResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private List<BadgeResponse> badges(User user, List<CompletedSectionRow> sections, List<ModuleResultRow> passed,
                                       int activeModules, Streak streak) {
        List<BadgeResponse> badges = new ArrayList<>();

        OffsetDateTime firstTheory = sections.stream()
                .filter(s -> Boolean.TRUE.equals(s.getTheory()))
                .map(CompletedSectionRow::getDoneAt).filter(Objects::nonNull)
                .findFirst().map(GamificationService::toOffset).orElse(null);
        badges.add(badge(BadgeType.PRIMER_PASO, firstTheory != null, firstTheory, firstTheory != null ? 1 : 0, 1));

        OffsetDateTime firstPassed = passed.isEmpty() ? null : toOffset(passed.get(0).getCompletedAt());
        badges.add(badge(BadgeType.PRIMER_MODULO, !passed.isEmpty(), firstPassed, Math.min(passed.size(), 1), 1));

        OffsetDateTime perfect = sections.stream()
                // Solo cuenta un 100 a la primera: con reintentos se podría llegar probando opciones
                .filter(s -> !Boolean.TRUE.equals(s.getTheory()) && s.getScore() != null
                        && s.getScore().compareTo(BigDecimal.valueOf(100)) >= 0
                        && s.getAttempts() != null && s.getAttempts() == 1)
                .map(CompletedSectionRow::getDoneAt).findFirst().map(GamificationService::toOffset).orElse(null);
        badges.add(badge(BadgeType.SIN_ERRORES, perfect != null, perfect, perfect != null ? 1 : 0, 1));

        badges.add(badge(BadgeType.RACHA_3, streak.longest() >= STREAK_BADGE_DAYS, streak.reachedBadgeAt(),
                Math.min(streak.longest(), STREAK_BADGE_DAYS), STREAK_BADGE_DAYS));

        boolean many = passed.size() >= MANY_MODULES_BADGE;
        badges.add(badge(BadgeType.CINCO_MODULOS, many, many ? toOffset(passed.get(MANY_MODULES_BADGE - 1).getCompletedAt()) : null,
                Math.min(passed.size(), MANY_MODULES_BADGE), MANY_MODULES_BADGE));

        List<ModuleResultRow> passedActive = passed.stream().filter(row -> Boolean.TRUE.equals(row.getActive())).toList();
        boolean all = activeModules > 0 && passedActive.size() >= activeModules;
        badges.add(badge(BadgeType.TEMARIO_COMPLETO, all,
                all ? toOffset(passedActive.get(passedActive.size() - 1).getCompletedAt()) : null,
                Math.min(passedActive.size(), Math.max(activeModules, 1)), Math.max(activeModules, 1)));

        int identity = (isSet(user.getDisplayName()) ? 1 : 0) + (isSet(user.getAvatar()) ? 1 : 0) + (isSet(user.getBio()) ? 1 : 0);
        // Sin fecha: depende de datos editables, no de un hecho del progreso
        badges.add(badge(BadgeType.IDENTIDAD, identity == 3, null, identity, 3));

        return badges;
    }

    private BadgeResponse badge(BadgeType type, boolean earned, OffsetDateTime earnedAt, int current, int target) {
        return new BadgeResponse(type.name(), type.getDisplayName(), type.getDescription(),
                earned, earned ? earnedAt : null, current, target);
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
