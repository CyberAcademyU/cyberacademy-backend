package com.cyberacademy.domains.module.dto;

import com.cyberacademy.domains.module.model.ProgressStatus;
import com.cyberacademy.domains.module.model.SectionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SectionProgressResponse(
        UUID sectionId,
        SectionType type,
        int order,
        String title,
        BigDecimal weightPercent,
        ProgressStatus status,
        BigDecimal score,
        int attempts,
        OffsetDateTime completedAt,
        boolean accessible,
        boolean locked,
        int questionCount
) {}
