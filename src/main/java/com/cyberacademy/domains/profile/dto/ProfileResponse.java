package com.cyberacademy.domains.profile.dto;

import com.cyberacademy.domains.gamification.dto.ActivityDayResponse;
import com.cyberacademy.domains.gamification.dto.BadgeResponse;
import com.cyberacademy.domains.gamification.dto.LeagueResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Perfil del usuario autenticado: datos editables + estadísticas calculadas. */
public record ProfileResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String role,
        String displayName,
        String publicName,
        String bio,
        String country,
        String avatar,
        boolean showInLeaderboard,
        OffsetDateTime createdAt,
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
