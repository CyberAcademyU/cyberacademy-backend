package com.cyberacademy.module.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Actualización parcial de un módulo: solo se aplican los campos enviados.
 */
public class UpdateModuleRequest {

    @Size(max = 150, message = "El título no puede superar los 150 caracteres")
    private String title;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
    private String description;

    @Min(value = 1, message = "El orden debe ser mayor o igual a 1")
    private Integer orderIndex;

    @DecimalMin(value = "0.00", message = "La nota mínima no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La nota mínima no puede superar 100")
    private BigDecimal passingScore;

    private Boolean active;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public BigDecimal getPassingScore() { return passingScore; }
    public void setPassingScore(BigDecimal passingScore) { this.passingScore = passingScore; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
