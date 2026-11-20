package com.cyberacademy.domains.gamification.dto;


public record LeaderboardEntryResponse(
        Integer rank,
        String name,
        String avatar,
        String photoUrl,
        long xp,
        long modulesCompleted,
        LeagueResponse league,
        boolean me
) {}
