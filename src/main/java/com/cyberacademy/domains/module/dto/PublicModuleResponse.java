package com.cyberacademy.domains.module.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ficha pública de un módulo para la página de exploración (sin sesión).
 * Solo datos de presentación: nada de contenido, preguntas ni identificadores internos.
 */
public record PublicModuleResponse(
        String code,
        String title,
        String description,
        String cover,
        int orderIndex,
        BigDecimal passingScore,
        List<String> topics
) {}
