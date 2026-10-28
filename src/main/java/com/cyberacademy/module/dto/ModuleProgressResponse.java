package com.cyberacademy.module.dto;

import java.util.List;

/**
 * Detalle del progreso de un estudiante en un módulo:
 * resumen + estado de cada una de sus 4 secciones.
 */
public record ModuleProgressResponse(
        ModuleSummaryResponse summary,
        List<SectionProgressResponse> sections
) {}
