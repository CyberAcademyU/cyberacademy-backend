package com.cyberacademy.module.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Registro de la nota de una sección.
 * Si completed no se envía, se asume que la sección quedó completada.
 */
public class RecordScoreRequest {

    @NotNull(message = "La nota es obligatoria")
    @DecimalMin(value = "0.00", message = "La nota no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La nota no puede superar 100")
    private BigDecimal score;

    private Boolean completed = Boolean.TRUE;

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
}
