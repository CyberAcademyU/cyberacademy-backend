package com.cyberacademy.module.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class PracticeAnswerRequest {

    @NotNull(message = "La pregunta es obligatoria")
    private UUID questionId;

    @NotNull(message = "La opción es obligatoria")
    private UUID optionId;

    public UUID getQuestionId() { return questionId; }
    public void setQuestionId(UUID questionId) { this.questionId = questionId; }

    public UUID getOptionId() { return optionId; }
    public void setOptionId(UUID optionId) { this.optionId = optionId; }
}
