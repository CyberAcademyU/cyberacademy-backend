package com.cyberacademy.domains.module.dto;

import com.cyberacademy.domains.module.model.ProgressStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resumen del progreso de un estudiante en un módulo.
 * El estado y la nota provienen de student_module_progress (calculados por la BD).
 */
public record ModuleSummaryResponse(
        UUID moduleId,
        String code,
        String title,
        String description,
        String cover,
        int orderIndex,
        ProgressStatus status,
        BigDecimal score,
        BigDecimal passingScore,
        long totalSections,
        long completedSections,
        BigDecimal progressPercent,
        boolean passed,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime lastActivityAt,
        boolean saved,
        boolean locked,
        /** Intentos usados en la práctica y máximo permitido (el admin puede reanudarlos). */
        int practiceAttempts,
        int maxPracticeAttempts
) {}
