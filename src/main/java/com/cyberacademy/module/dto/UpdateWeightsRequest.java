package com.cyberacademy.module.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Actualización conjunta de los pesos de las secciones de un módulo.
 * Deben enviarse todas las secciones y la suma debe ser exactamente 100.
 */
public class UpdateWeightsRequest {

    @NotEmpty(message = "Debes enviar los pesos de todas las secciones")
    @Valid
    private List<SectionWeight> weights;

    public List<SectionWeight> getWeights() { return weights; }
    public void setWeights(List<SectionWeight> weights) { this.weights = weights; }

    public static class SectionWeight {

        @NotNull(message = "El identificador de la sección es obligatorio")
        private UUID sectionId;

        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "0.01", message = "El peso debe ser mayor que 0")
        @DecimalMax(value = "100.00", message = "El peso no puede superar 100")
        private BigDecimal weightPercent;

        public UUID getSectionId() { return sectionId; }
        public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }

        public BigDecimal getWeightPercent() { return weightPercent; }
        public void setWeightPercent(BigDecimal weightPercent) { this.weightPercent = weightPercent; }
    }
}
