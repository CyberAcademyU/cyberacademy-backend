package com.cyberacademy.domains.module.dto;

import java.util.UUID;

/**
 * Resultado de comprobar la pregunta de comprensión de una teoría.
 * feedback: por qué la opción elegida es correcta o no.
 * explanation: la lección completa de la pregunta.
 * correctOptionId: opción correcta, resaltada en pantalla cuando la respuesta falla.
 * La sesión queda completada con cualquier respuesta: fallar desbloquea la siguiente.
 */
public record CheckAnswerResponse(
        boolean correct,
        boolean sectionCompleted,
        String message,
        String feedback,
        String explanation,
        UUID correctOptionId
) {}
