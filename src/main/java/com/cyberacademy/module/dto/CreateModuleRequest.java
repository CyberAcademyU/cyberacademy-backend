package com.cyberacademy.module.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class CreateModuleRequest {

    @NotBlank(message = "El código del módulo es obligatorio")
    @Size(max = 50, message = "El código no puede superar los 50 caracteres")
    private String code;

    @NotBlank(message = "El título del módulo es obligatorio")
    @Size(max = 150, message = "El título no puede superar los 150 caracteres")
    private String title;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
    private String description;

    @Min(value = 1, message = "El orden debe ser mayor o igual a 1")
    private Integer orderIndex = 1;

    @DecimalMin(value = "0.00", message = "La nota mínima no puede ser negativa")
    @DecimalMax(value = "100.00", message = "La nota mínima no puede superar 100")
    private BigDecimal passingScore = new BigDecimal("70.00");

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }

    public BigDecimal getPassingScore() { return passingScore; }
    public void setPassingScore(BigDecimal passingScore) { this.passingScore = passingScore; }
}
