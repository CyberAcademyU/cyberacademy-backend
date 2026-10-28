package com.cyberacademy.module.dto;

import com.cyberacademy.module.entity.SectionType;

import java.math.BigDecimal;
import java.util.UUID;

public record SectionResponse(
        UUID id,
        SectionType type,
        int order,
        String title,
        BigDecimal weightPercent
) {}
