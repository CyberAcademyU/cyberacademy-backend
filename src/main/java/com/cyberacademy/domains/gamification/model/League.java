package com.cyberacademy.domains.gamification.model;

/**
 * Ligas por XP acumulada. Son permanentes: la XP nunca se pierde, así que
 * un estudiante solo puede subir de liga.
 */
public enum League {
    RECLUTA("Recluta", 0),
    ANALISTA("Analista", 150),
    DEFENSOR("Defensor", 450),
    CENTINELA("Centinela", 900),
    ELITE("Élite", 1500);

    private final String displayName;
    private final long minXp;

    League(String displayName, long minXp) {
        this.displayName = displayName;
        this.minXp = minXp;
    }

    public String getDisplayName() { return displayName; }
    public long getMinXp() { return minXp; }

    public static League forXp(long xp) {
        League result = RECLUTA;
        for (League league : values()) {
            if (xp >= league.minXp) result = league;
        }
        return result;
    }

    /** Siguiente liga, o null si ya es la última. */
    public League next() {
        int index = ordinal() + 1;
        return index < values().length ? values()[index] : null;
    }
}
