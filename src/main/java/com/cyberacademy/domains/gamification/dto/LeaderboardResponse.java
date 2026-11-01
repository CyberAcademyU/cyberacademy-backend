package com.cyberacademy.domains.gamification.dto;

import java.util.List;

/**
 * @param period     WEEK (desde el lunes) o ALL
 * @param entries    las primeras posiciones con XP en el periodo
 * @param me         mi fila; rank es null si no tengo XP en el periodo o me oculté
 * @param totalRanked cuántos estudiantes tienen posición en el periodo
 * @param hidden     true si elegí no aparecer en la clasificación
 */
public record LeaderboardResponse(
        String period,
        List<LeaderboardEntryResponse> entries,
        LeaderboardEntryResponse me,
        long totalRanked,
        boolean hidden
) {}
