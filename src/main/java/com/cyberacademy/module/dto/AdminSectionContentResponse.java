package com.cyberacademy.module.dto;

import com.cyberacademy.module.entity.SectionType;

import java.util.List;
import java.util.UUID;

/** Contenido editable de una sesión (título, texto, preguntas y respuestas). */
public record AdminSectionContentResponse(
        UUID sectionId,
        SectionType type,
        int order,
        String title,
        String content,
        List<AdminQuestionResponse> questions
) {}
