package com.cyberacademy.module.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Respuestas del único intento de la práctica. */
public class SubmitPracticeRequest {

    @NotEmpty(message = "Debes responder las preguntas de la práctica")
    @Valid
    private List<PracticeAnswerRequest> answers;

    public List<PracticeAnswerRequest> getAnswers() { return answers; }
    public void setAnswers(List<PracticeAnswerRequest> answers) { this.answers = answers; }
}
