package com.cyberacademy.domains.module.controller;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.module.dto.ModuleSummaryResponse;
import com.cyberacademy.domains.module.service.ModuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/progress")
public class ProgressController {

    private final ModuleService moduleService;

    public ProgressController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    /** Resumen del progreso del estudiante autenticado en todos los módulos. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ModuleSummaryResponse>>> myProgress() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Progreso obtenido correctamente",
                moduleService.listMyProgress()));
    }

    /** Resumen del progreso de un estudiante concreto (solo ADMIN). */
    @GetMapping("/student/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ModuleSummaryResponse>>> studentProgress(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Progreso del estudiante obtenido correctamente",
                moduleService.listStudentProgress(userId)));
    }

    /** Reanuda los intentos de la práctica de un estudiante que no la aprobó (solo ADMIN). */
    @PostMapping("/student/{userId}/modules/{moduleId}/practice/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ModuleSummaryResponse>>> resetPracticeAttempts(
            @PathVariable UUID userId, @PathVariable UUID moduleId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Intentos reanudados correctamente",
                moduleService.resetPracticeAttempts(userId, moduleId)));
    }
}
