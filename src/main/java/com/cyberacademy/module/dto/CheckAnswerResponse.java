package com.cyberacademy.module.dto;

/** Resultado de comprobar la pregunta de comprensión de una teoría. */
public record CheckAnswerResponse(
        boolean correct,
        boolean sectionCompleted,
        String message
) {}
