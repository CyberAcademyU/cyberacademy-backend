package com.cyberacademy.module.dto;

import java.util.UUID;

/** Datos públicos mínimos de un módulo recién creado. */
public record ModuleCreatedEvent(UUID id, String title) {}
