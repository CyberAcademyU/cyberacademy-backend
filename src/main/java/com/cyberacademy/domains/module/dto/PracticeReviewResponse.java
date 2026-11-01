package com.cyberacademy.domains.module.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Corrección del último intento de la práctica, pregunta por pregunta. La opción correcta y la
 * explicación solo se incluyen cuando la práctica está cerrada (aprobada o sin intentos): con
 * intentos restantes llegan a null.
 */
public record PracticeReviewResponse(
        BigDecimal score,
        BigDecimal passingScore,
        boolean passed,
        int correctCount,
        int totalQuestions,
        List<QuestionReview> questions
) {
    public record QuestionReview(
            UUID id,
            int order,
            String prompt,
            boolean correct,
            /** Retroalimentación de la opción elegida. */
            String feedback,
            String explanation,
            List<OptionReview> options
    ) {}

    public record OptionReview(
            UUID id,
            int order,
            String text,
            /** null mientras queden intentos y no esté aprobada. */
            Boolean correct,
            boolean chosen
    ) {}
}
