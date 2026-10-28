package com.cyberacademy.admin.service;

import com.cyberacademy.admin.dto.AdminOverviewResponse;
import com.cyberacademy.admin.dto.ModuleCompletionResponse;
import com.cyberacademy.admin.dto.RecentActivityResponse;
import com.cyberacademy.admin.dto.StudentOverviewResponse;
import com.cyberacademy.auth.entity.UserStatus;
import com.cyberacademy.module.entity.ProgressStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Estadísticas del panel de administración calculadas con SQL sobre las tablas
 * de progreso. Se usa JdbcTemplate para evitar cargar todas las entidades y
 * poder hacer las agregaciones directamente en PostgreSQL.
 */
@Service
public class AdminDashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 8;

    private static final String STUDENTS_SQL = """
            SELECT u.id,
                   u.first_name,
                   u.last_name,
                   u.email,
                   u.status,
                   u.created_at,
                   COALESCE(agg.modules_completed, 0) AS modules_completed,
                   (SELECT COUNT(*) FROM modules WHERE is_active) AS modules_total,
                   agg.average_score,
                   COALESCE(ROUND(agg.progress_sum / NULLIF((SELECT COUNT(*) FROM modules WHERE is_active), 0), 2), 0) AS progress_percent,
                   (SELECT MAX(p.updated_at)
                      FROM student_module_progress p
                      JOIN modules m ON m.id = p.module_id AND m.is_active
                     WHERE p.user_id = u.id) AS last_activity_at
              FROM users u
              LEFT JOIN (
                  SELECT v.user_id,
                         COUNT(*) FILTER (WHERE v.status = 'COMPLETED') AS modules_completed,
                         ROUND(AVG(v.module_score) FILTER (WHERE v.status IN ('COMPLETED', 'FAILED')), 2) AS average_score,
                         SUM(v.progress_percent) AS progress_sum
                    FROM v_student_module_summary v
                    JOIN modules m ON m.id = v.module_id AND m.is_active
                   GROUP BY v.user_id
              ) agg ON agg.user_id = u.id
             WHERE u.role = 'STUDENT'
             ORDER BY u.first_name ASC, u.last_name ASC
            """;

    private static final String MODULE_STATS_SQL = """
            SELECT m.id,
                   m.code,
                   m.title,
                   m.order_index,
                   COUNT(v.user_id) FILTER (WHERE v.status = 'COMPLETED')   AS completed_students,
                   COUNT(v.user_id) FILTER (WHERE v.status = 'IN_PROGRESS') AS in_progress_students,
                   COUNT(v.user_id) FILTER (WHERE v.status = 'FAILED')      AS failed_students,
                   COUNT(v.user_id)                                        AS started_students
              FROM modules m
              LEFT JOIN v_student_module_summary v ON v.module_id = m.id
             WHERE m.is_active
             GROUP BY m.id, m.code, m.title, m.order_index
             ORDER BY m.order_index ASC, m.code ASC
            """;

    private static final String RECENT_ACTIVITY_SQL = """
            SELECT u.first_name || ' ' || u.last_name AS student_name,
                   m.code,
                   m.title,
                   s.title AS section_title,
                   sp.score,
                   sp.status,
                   sp.updated_at
              FROM student_section_progress sp
              JOIN student_module_progress p ON p.id = sp.module_progress_id
              JOIN users u   ON u.id = p.user_id
              JOIN modules m ON m.id = p.module_id
              JOIN module_sections s ON s.id = sp.section_id
             WHERE u.role = 'STUDENT'
             ORDER BY sp.updated_at DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<StudentOverviewResponse> listStudents() {
        return jdbcTemplate.query(STUDENTS_SQL, this::mapStudent);
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
        return jdbcTemplate.query(MODULE_STATS_SQL, (rs, rowNum) -> {
            long completed = rs.getLong("completed_students");

            BigDecimal completionPercent = totalStudents == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP);

            return new ModuleCompletionResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getString("title"),
                    rs.getInt("order_index"),
                    completed,
                    rs.getLong("in_progress_students"),
                    rs.getLong("failed_students"),
                    rs.getLong("started_students"),
                    completionPercent);
        });
    }

    private List<RecentActivityResponse> listRecentActivity() {
        return jdbcTemplate.query(RECENT_ACTIVITY_SQL, (rs, rowNum) -> new RecentActivityResponse(
                rs.getString("student_name"),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("section_title"),
                rs.getBigDecimal("score"),
                ProgressStatus.valueOf(rs.getString("status")),
                rs.getObject("updated_at", OffsetDateTime.class)
        ), RECENT_ACTIVITY_LIMIT);
    }

    private StudentOverviewResponse mapStudent(ResultSet rs, int rowNum) throws SQLException {
        return new StudentOverviewResponse(
                rs.getObject("id", UUID.class),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                UserStatus.valueOf(rs.getString("status")),
                rs.getInt("modules_completed"),
                rs.getInt("modules_total"),
                rs.getBigDecimal("average_score"),
                rs.getBigDecimal("progress_percent"),
                rs.getObject("last_activity_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        );
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
