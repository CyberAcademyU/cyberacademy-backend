package com.cyberacademy.domains.module.dto;

/**
 * Resultado de comprobar la pregunta de comprensión de una teoría.
 * feedback: por qué la opción elegida es correcta o no (nunca revela otra opción).
 * explanation: la lección completa; solo cuando la respuesta es correcta.
 */
public record CheckAnswerResponse(
        boolean correct,
        boolean sectionCompleted,
        String message,
        String feedback,
        String explanation
) {}
