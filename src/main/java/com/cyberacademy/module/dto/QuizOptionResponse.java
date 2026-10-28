package com.cyberacademy.module.dto;

import java.util.UUID;

/** Opción de respuesta tal como la ve el estudiante (sin indicar cuál es correcta). */
public record QuizOptionResponse(
        UUID id,
        int order,
        String text
) {}
