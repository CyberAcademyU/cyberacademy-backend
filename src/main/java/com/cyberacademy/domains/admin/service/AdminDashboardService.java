package com.cyberacademy.domains.admin.service;

import com.cyberacademy.domains.admin.dto.AdminOverviewResponse;
import com.cyberacademy.domains.admin.dto.ModuleCompletionResponse;
import com.cyberacademy.domains.admin.dto.RecentActivityResponse;
import com.cyberacademy.domains.admin.dto.StudentOverviewResponse;
import com.cyberacademy.domains.admin.model.AdminModuleStatRow;
import com.cyberacademy.domains.admin.model.AdminStudentRow;
import com.cyberacademy.domains.admin.repository.AdminDashboardRepository;
import com.cyberacademy.domains.auth.model.UserStatus;
import com.cyberacademy.domains.module.model.ProgressStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Estadísticas del panel de administración. Las agregaciones las hacen funciones
 * de PostgreSQL (db/functions/admin_dashboard.sql) para no cargar todas las
 * entidades en memoria; aquí solo se convierten a DTO.
 */
@Service
public class AdminDashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 8;

    private final AdminDashboardRepository adminDashboardRepository;

    public AdminDashboardService(AdminDashboardRepository adminDashboardRepository) {
        this.adminDashboardRepository = adminDashboardRepository;
    }

    @Transactional(readOnly = true)
    public List<StudentOverviewResponse> listStudents() {
        return adminDashboardRepository.students().stream().map(this::toStudent).toList();
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        List<StudentOverviewResponse> students = listStudents();
        List<ModuleCompletionResponse> modules = listModuleStats(students.size());

        long activeStudents = students.stream()
                .filter(student -> student.status() == UserStatus.ACTIVE)
                .count();

        long studentsCompletedAll = modules.isEmpty()
                ? 0
                : students.stream()
                    .filter(student -> student.modulesCompleted() >= modules.size())
                    .count();

        BigDecimal averageScore = average(students.stream()
                .map(StudentOverviewResponse::averageScore)
                .filter(Objects::nonNull)
                .toList());

        BigDecimal averageProgress = average(students.stream()
                .map(StudentOverviewResponse::progressPercent)
                .toList());

        return new AdminOverviewResponse(
                students.size(),
                activeStudents,
                studentsCompletedAll,
                averageScore,
                averageProgress == null ? BigDecimal.ZERO : averageProgress,
                modules.size(),
                modules,
                listRecentActivity());
    }

    private List<ModuleCompletionResponse> listModuleStats(long totalStudents) {
        return adminDashboardRepository.moduleStats().stream()
                .map(row -> toModuleCompletion(row, totalStudents))
                .toList();
    }

    private List<RecentActivityResponse> listRecentActivity() {
        return adminDashboardRepository.recentActivity(RECENT_ACTIVITY_LIMIT).stream()
                .map(row -> new RecentActivityResponse(
                        row.getStudentName(),
                        row.getCode(),
                        row.getTitle(),
                        row.getSectionTitle(),
                        row.getScore(),
                        ProgressStatus.valueOf(row.getStatus()),
                        toOffset(row.getUpdatedAt())))
                .toList();
    }

    private ModuleCompletionResponse toModuleCompletion(AdminModuleStatRow row, long totalStudents) {
        long completed = row.getCompletedStudents();

        BigDecimal completionPercent = totalStudents == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP);

        return new ModuleCompletionResponse(
                row.getId(),
                row.getCode(),
                row.getTitle(),
                row.getOrderIndex(),
                completed,
                row.getInProgressStudents(),
                row.getFailedStudents(),
                row.getStartedStudents(),
                completionPercent);
    }

    private StudentOverviewResponse toStudent(AdminStudentRow row) {
        return new StudentOverviewResponse(
                row.getId(),
                row.getFirstName(),
                row.getLastName(),
                row.getEmail(),
                UserStatus.valueOf(row.getStatus()),
                row.getModulesCompleted().intValue(),
                row.getModulesTotal().intValue(),
                row.getTotalScore(),
                row.getGradedModules().intValue(),
                row.getAverageScore(),
                row.getProgressPercent(),
                toOffset(row.getLastActivityAt()),
                toOffset(row.getCreatedAt()));
    }

    /** Las funciones devuelven timestamptz, que llega como Instant; los DTO usan OffsetDateTime. */
    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = values.stream().filter(Objects::nonNull).count();
        if (count == 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
