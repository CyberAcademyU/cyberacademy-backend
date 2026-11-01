package com.cyberacademy.domains.module.dto;

import com.cyberacademy.domains.module.model.SectionType;

import java.math.BigDecimal;
import java.util.UUID;

public record SectionResponse(
        UUID id,
        SectionType type,
        int order,
        String title,
        BigDecimal weightPercent
) {}
