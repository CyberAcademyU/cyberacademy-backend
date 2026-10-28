package com.cyberacademy.module.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ModuleResponse(
        UUID id,
        String code,
        String title,
        String description,
        int orderIndex,
        BigDecimal passingScore,
        boolean active,
        List<SectionResponse> sections
) {}
