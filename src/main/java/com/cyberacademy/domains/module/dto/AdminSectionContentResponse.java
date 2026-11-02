package com.cyberacademy.domains.module.dto;

import com.cyberacademy.domains.module.model.SectionType;

import java.util.List;
import java.util.UUID;

/** Contenido editable de una sesión (título, texto, preguntas y respuestas). */
public record AdminSectionContentResponse(
        UUID sectionId,
        SectionType type,
        int order,
        String title,
        String content,
        String imageUrl1,
        String imageUrl2,
        String videoUrl,
        List<AdminQuestionResponse> questions
) {}
