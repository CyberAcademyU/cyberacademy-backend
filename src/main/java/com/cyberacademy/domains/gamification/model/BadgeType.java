package com.cyberacademy.domains.gamification.model;

/**
 * Catálogo de insignias. No se guardan en BD: se deducen del progreso
 * (ver GamificationService), así que no se pueden falsificar desde el cliente.
 */
public enum BadgeType {
    PRIMER_PASO("Primer paso", "Completa tu primera teoría."),
    PRIMER_MODULO("Módulo superado", "Aprueba tu primer módulo."),
    SIN_ERRORES("Sin errores", "Saca 100 puntos en una práctica."),
    RACHA_3("Tres días seguidos", "Avanza en tus módulos tres días seguidos."),
    CINCO_MODULOS("Cinco módulos", "Aprueba cinco módulos."),
    TEMARIO_COMPLETO("Temario completo", "Aprueba todos los módulos publicados."),
    IDENTIDAD("Identidad", "Elige alias y avatar y escribe tu biografía.");

    private final String displayName;
    private final String description;

    BadgeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
