package com.cyberacademy.module.dto;

import com.cyberacademy.module.entity.ProgressStatus;
import com.cyberacademy.module.entity.SectionType;

import java.util.List;
import java.util.UUID;

/** Contenido de la sesión que el estudiante tiene desbloqueada en este momento. */
public record SectionContentResponse(
        UUID sectionId,
        SectionType type,
        int order,
        String title,
        String content,
        ProgressStatus status,
        boolean accessible,
        List<QuizQuestionResponse> questions
) {}
