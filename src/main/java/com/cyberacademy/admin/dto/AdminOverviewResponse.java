package com.cyberacademy.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumen global del panel de administración, calculado a partir
 * del progreso real de los estudiantes.
 */
public record AdminOverviewResponse(
        long totalStudents,
        long activeStudents,
        long studentsCompletedAll,
        BigDecimal averageScore,
        BigDecimal averageProgress,
        int totalActiveModules,
        List<ModuleCompletionResponse> modules,
        List<RecentActivityResponse> recentActivity
) {}
