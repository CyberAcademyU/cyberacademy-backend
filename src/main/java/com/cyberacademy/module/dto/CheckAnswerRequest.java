package com.cyberacademy.module.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Respuesta del estudiante a la pregunta de comprensión de una teoría. */
public class CheckAnswerRequest {

    @NotNull(message = "La pregunta es obligatoria")
    private UUID questionId;

    @NotNull(message = "La opción es obligatoria")
    private UUID optionId;

    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }

    public UUID getOptionId() { return optionId; }
    public void setOptionId(UUID optionId) { this.optionId = optionId; }
}
