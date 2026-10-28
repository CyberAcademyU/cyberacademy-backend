package com.cyberacademy.module.dto;

import java.util.List;
import java.util.UUID;

/** Pregunta que se muestra al estudiante, sin revelar la respuesta correcta. */
public record QuizQuestionResponse(
        UUID id,
        int order,
        String prompt,
        List<QuizOptionResponse> options
) {}
