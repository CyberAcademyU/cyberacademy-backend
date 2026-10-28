package com.cyberacademy.admin.dto;

import com.cyberacademy.module.entity.ProgressStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Última actividad registrada por un estudiante en una sección.
 */
public record RecentActivityResponse(
        String studentName,
        String moduleCode,
        String moduleTitle,
        String sectionTitle,
        BigDecimal score,
        ProgressStatus status,
        OffsetDateTime occurredAt
) {}
