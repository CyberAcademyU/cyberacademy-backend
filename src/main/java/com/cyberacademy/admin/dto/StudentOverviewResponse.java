package com.cyberacademy.admin.dto;

import com.cyberacademy.auth.entity.UserStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila del listado de estudiantes del panel de administración,
 * con sus módulos completados, la nota de cada módulo y el total acumulado.
 *
 * <p>{@code totalScore} es la suma de las notas de los módulos ya calificados
 * y {@code gradedModules} indica cuántos módulos aportan a ese total. La nota
 * máxima posible es {@code modulesTotal * 100}.</p>
 */
public record StudentOverviewResponse(
        UUID userId,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        int modulesCompleted,
        int modulesTotal,
        BigDecimal totalScore,
        int gradedModules,
        BigDecimal averageScore,
        BigDecimal progressPercent,
        OffsetDateTime lastActivityAt,
        OffsetDateTime createdAt
) {}
