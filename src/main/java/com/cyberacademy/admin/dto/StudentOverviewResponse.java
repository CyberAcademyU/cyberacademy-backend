package com.cyberacademy.admin.dto;

import com.cyberacademy.auth.entity.UserStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila del listado de estudiantes del panel de administración,
 * con sus módulos completados y su avance global.
 */
public record StudentOverviewResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        int modulesCompleted,
        int modulesTotal,
        BigDecimal averageScore,
        BigDecimal progressPercent,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt
) {}
