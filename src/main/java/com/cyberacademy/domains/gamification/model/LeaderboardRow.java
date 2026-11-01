package com.cyberacademy.domains.gamification.model;

import java.util.UUID;

/** Fila de fn_leaderboard (proyección de solo lectura). */
public interface LeaderboardRow {
    UUID getUserId();
    String getPublicName();
    String getAvatar();
    Boolean getVisible();
    Long getXp();
    Long getTotalXp();
    Long getModulesCompleted();
    /** Puesto en el periodo; null si no es visible o no tiene XP. */
    Long getRanking();
}
