package com.cyberacademy.domains.module.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Guarda de una vez el título, el contenido y las preguntas de una sesión.
 * Las teorías deben tener exactamente 1 pregunta; la práctica, al menos 1.
 */
public class SaveSectionContentRequest {

    @Size(max = 150, message = "El título no puede superar los 150 caracteres")
    private String title;

    @Size(max = 20000, message = "El contenido no puede superar los 20000 caracteres")
    private String content;

    @Size(max = 2048) private String imageUrl1;
    @Size(max = 2048) private String imageUrl2;
    @Size(max = 2048) private String videoUrl;
    private boolean imageUrl1Provided;
    private boolean imageUrl2Provided;
    private boolean videoUrlProvided;

    public String getImageUrl1() { return imageUrl1; }
    public void setImageUrl1(String value) { imageUrl1 = value; imageUrl1Provided = true; }
    public boolean isImageUrl1Provided() { return imageUrl1Provided; }
    public String getImageUrl2() { return imageUrl2; }
    public void setImageUrl2(String value) { imageUrl2 = value; imageUrl2Provided = true; }
    public boolean isImageUrl2Provided() { return imageUrl2Provided; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String value) { videoUrl = value; videoUrlProvided = true; }
    public boolean isVideoUrlProvided() { return videoUrlProvided; }

    @NotNull(message = "Debes enviar las preguntas de la sesión")
    @Valid
    private List<QuestionInput> questions;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<QuestionInput> getQuestions() { return questions; }
    public void setQuestions(List<QuestionInput> questions) { this.questions = questions; }

    public static class QuestionInput {

        @NotBlank(message = "El enunciado de la pregunta es obligatorio")
        @Size(max = 2000, message = "El enunciado no puede superar los 2000 caracteres")
        private String prompt;

        @Size(max = 2000, message = "La explicación no puede superar los 2000 caracteres")
        private String explanation;

        @NotNull(message = "Cada pregunta debe tener opciones")
        @Size(min = 2, max = 6, message = "Cada pregunta debe tener entre 2 y 6 opciones")
        @Valid
        private List<OptionInput> options;

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }

        public List<OptionInput> getOptions() { return options; }
        public void setOptions(List<OptionInput> options) { this.options = options; }
    }

    public static class OptionInput {

        @NotBlank(message = "El texto de cada opción es obligatorio")
        @Size(max = 500, message = "Cada opción no puede superar los 500 caracteres")
        private String text;

        private boolean correct;

        @Size(max = 1000, message = "La retroalimentación de cada opción no puede superar los 1000 caracteres")
        private String feedback;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }

        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }
    }
}
