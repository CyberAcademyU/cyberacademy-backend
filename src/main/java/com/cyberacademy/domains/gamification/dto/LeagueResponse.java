package com.cyberacademy.domains.gamification.dto;

import com.cyberacademy.domains.gamification.model.League;

/** Liga actual y lo que falta para la siguiente (next* es null en la última liga). */
public record LeagueResponse(
        String code,
        String name,
        long minXp,
        String nextCode,
        String nextName,
        Long nextMinXp
) {
    public static LeagueResponse of(League league) {
        League next = league.next();
        return new LeagueResponse(
                league.name(),
                league.getDisplayName(),
                league.getMinXp(),
                next == null ? null : next.name(),
                next == null ? null : next.getDisplayName(),
                next == null ? null : next.getMinXp());
    }
}
