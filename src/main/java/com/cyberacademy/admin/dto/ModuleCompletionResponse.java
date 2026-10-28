package com.cyberacademy.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Estadísticas de un módulo: cuántos estudiantes lo completaron,
 * lo tienen en curso, lo suspendieron o lo empezaron.
 */
public record ModuleCompletionResponse(
        UUID moduleId,
        String code,
        String title,
        int orderIndex,
        long completedStudents,
        long inProgressStudents,
        long failedStudents,
        long startedStudents,
        BigDecimal completionPercent
) {}
