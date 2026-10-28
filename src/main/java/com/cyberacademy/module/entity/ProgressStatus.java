package com.cyberacademy.module.entity;

/**
 * Estado del progreso de un estudiante (en un módulo o en una sección).
 * El nombre coincide con el ENUM nativo de PostgreSQL "progress_status".
 */
public enum ProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
