package com.cyberacademy.domains.gamification.dto;

import java.time.LocalDate;

/** Sesiones completadas en un día (zona horaria de la academia). */
public record ActivityDayResponse(LocalDate date, int count) {}
