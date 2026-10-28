package com.cyberacademy.admin.controller;

import com.cyberacademy.admin.dto.AdminOverviewResponse;
import com.cyberacademy.admin.dto.StudentOverviewResponse;
import com.cyberacademy.admin.service.AdminDashboardService;
import com.cyberacademy.auth.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Panel de administración: estadísticas globales y listado real de estudiantes.
 * Todo el controlador está restringido a ADMIN.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /** Métricas globales, finalización por módulo y últimas actividades. */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Resumen de administración obtenido correctamente",
                adminDashboardService.getOverview()));
    }

    /** Listado de estudiantes con módulos completados, nota media y avance. */
    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<StudentOverviewResponse>>> students() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Estudiantes obtenidos correctamente",
                adminDashboardService.listStudents()));
    }
}
