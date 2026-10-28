package com.cyberacademy.module.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Actualización parcial de una sección (título y/o peso).
 * La suma de los pesos de las 4 secciones debe seguir siendo 100.
 */
public class UpdateSectionRequest {

    @Size(max = 150, message = "El título no puede superar los 150 caracteres")
    private String title;

    @DecimalMin(value = "0.01", message = "El peso debe ser mayor que 0")
    @DecimalMax(value = "100.00", message = "El peso no puede superar 100")
    private BigDecimal weightPercent;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getWeightPercent() { return weightPercent; }
    public void setWeightPercent(BigDecimal weightPercent) { this.weightPercent = weightPercent; }
}
