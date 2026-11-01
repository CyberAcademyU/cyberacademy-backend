package com.cyberacademy.domains.gamification.dto;

import java.time.OffsetDateTime;

/** Insignia del catálogo con el avance del estudiante hacia ella. */
public record BadgeResponse(
        String code,
        String name,
        String description,
        boolean earned,
        OffsetDateTime earnedAt,
        int current,
        int target
) {}
