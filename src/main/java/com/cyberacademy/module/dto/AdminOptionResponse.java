package com.cyberacademy.module.dto;

import java.util.UUID;

/** Opción tal como se devuelve al administrador, indicando si es la correcta. */
public record AdminOptionResponse(
        UUID id,
        int order,
        String text,
        boolean correct
) {}
