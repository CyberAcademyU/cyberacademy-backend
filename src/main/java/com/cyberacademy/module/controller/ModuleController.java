package com.cyberacademy.module.controller;

import com.cyberacademy.auth.dto.ApiResponse;
import com.cyberacademy.module.dto.*;
import com.cyberacademy.module.service.ModuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/modules")
public class ModuleController {

    private final ModuleService moduleService;

    public ModuleController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    /** Catálogo de módulos (por defecto solo los activos). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Módulos obtenidos correctamente",
                moduleService.listModules(includeInactive)));
    }

    /** Detalle de un módulo con sus 4 secciones. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ModuleResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Módulo obtenido correctamente",
                moduleService.getModule(id)));
    }

    /** Crear módulo (solo ADMIN). Las 4 secciones las genera la base de datos. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ModuleResponse>> create(@Valid @RequestBody CreateModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Módulo creado correctamente",
                moduleService.createModule(request)));
    }

    /** Actualizar datos del módulo (solo ADMIN). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ModuleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Módulo actualizado correctamente",
                moduleService.updateModule(id, request)));
    }

    /** Actualizar título y/o peso de una sección (solo ADMIN). */
    @PutMapping("/{moduleId}/sections/{sectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SectionResponse>> updateSection(
            @PathVariable UUID moduleId,
            @PathVariable UUID sectionId,
            @Valid @RequestBody UpdateSectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Sección actualizada correctamente",
                moduleService.updateSection(moduleId, sectionId, request)));
    }

    /** Actualizar los pesos de las 4 secciones a la vez (solo ADMIN). Deben sumar 100. */
    @PutMapping("/{moduleId}/weights")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SectionResponse>>> updateWeights(
            @PathVariable UUID moduleId,
            @Valid @RequestBody UpdateWeightsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Pesos actualizados correctamente",
                moduleService.updateSectionWeights(moduleId, request)));
    }

    /** Progreso del estudiante autenticado en un módulo concreto. */
    @GetMapping("/{moduleId}/progress")
    public ResponseEntity<ApiResponse<ModuleProgressResponse>> myProgress(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Progreso obtenido correctamente",
                moduleService.getMyProgress(moduleId)));
    }

}
