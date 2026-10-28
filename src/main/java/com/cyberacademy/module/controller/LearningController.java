package com.cyberacademy.module.controller;

import com.cyberacademy.auth.dto.ApiResponse;
import com.cyberacademy.module.dto.*;
import com.cyberacademy.module.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/modules")
public class LearningController {

    private final QuizService quizService;

    public LearningController(QuizService quizService) {
        this.quizService = quizService;
    }

    /** Sesión desbloqueada del estudiante, con su contenido y preguntas. */
    @GetMapping("/{moduleId}/sections/{sectionId}")
    public ResponseEntity<ApiResponse<SectionContentResponse>> getSection(
            @PathVariable UUID moduleId,
            @PathVariable UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Sesión obtenida correctamente",
                quizService.getStudentSection(moduleId, sectionId)));
    }

    /** Comprobar la pregunta de comprensión de una teoría. */
    @PostMapping("/{moduleId}/sections/{sectionId}/check")
    public ResponseEntity<ApiResponse<CheckAnswerResponse>> checkTheory(
            @PathVariable UUID moduleId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody CheckAnswerRequest request) {
        CheckAnswerResponse result = quizService.checkTheoryAnswer(moduleId, sectionId, request);
        return ResponseEntity.ok(ApiResponse.ok(result.message(), result));
    }

    /** Enviar la práctica (un solo intento) y recibir la nota calculada. */
    @PostMapping("/{moduleId}/practice/submit")
    public ResponseEntity<ApiResponse<ModuleProgressResponse>> submitPractice(
            @PathVariable UUID moduleId,
            @Valid @RequestBody SubmitPracticeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Práctica corregida correctamente",
                quizService.submitPractice(moduleId, request)));
    }

    /** Contenido y preguntas de las 4 secciones (solo ADMIN). */
    @GetMapping("/{moduleId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminSectionContentResponse>>> getContent(
            @PathVariable UUID moduleId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Contenido del módulo obtenido correctamente",
                quizService.getModuleContent(moduleId)));
    }

    /** Guardar título, contenido y preguntas de una sesión (solo ADMIN). */
    @PutMapping("/{moduleId}/sections/{sectionId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminSectionContentResponse>> saveContent(
            @PathVariable UUID moduleId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody SaveSectionContentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Contenido de la sesión guardado correctamente",
                quizService.saveSectionContent(moduleId, sectionId, request)));
    }
}
