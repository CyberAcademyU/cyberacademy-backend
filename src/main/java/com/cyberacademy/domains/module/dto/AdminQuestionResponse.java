package com.cyberacademy.domains.module.dto;

import java.util.List;
import java.util.UUID;

/** Pregunta con su solución, solo para el editor del administrador. */
public record AdminQuestionResponse(
        UUID id,
        int order,
        String prompt,
        String explanation,
        List<AdminOptionResponse> options
) {}
