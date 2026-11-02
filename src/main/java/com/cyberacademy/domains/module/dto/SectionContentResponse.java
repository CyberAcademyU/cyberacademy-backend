package com.cyberacademy.domains.module.dto;

import com.cyberacademy.domains.module.model.ProgressStatus;
import com.cyberacademy.domains.module.model.SectionType;

import java.util.List;
import java.util.UUID;

/** Contenido de la sesión que el estudiante tiene desbloqueada en este momento. */
public record SectionContentResponse(
        UUID sectionId,
        SectionType type,
        int order,
        String title,
        String content,
        String imageUrl1,
        String imageUrl2,
        String videoUrl,
        ProgressStatus status,
        boolean accessible,
        List<QuizQuestionResponse> questions
) {}
