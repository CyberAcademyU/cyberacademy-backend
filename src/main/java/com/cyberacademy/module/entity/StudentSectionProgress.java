package com.cyberacademy.module.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Progreso de un estudiante en una sección concreta (tabla "student_section_progress").
 * Cada inserción/actualización dispara el recálculo del módulo en la base de datos.
 */
@Entity
@Table(name = "student_section_progress")
public class StudentSectionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_progress_id", nullable = false)
    private StudentModuleProgress moduleProgress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private ModuleSection section;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM) // ENUM nativo de PostgreSQL (progress_status)
    @Column(nullable = false)
    private ProgressStatus status = ProgressStatus.NOT_STARTED;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public StudentSectionProgress() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public StudentModuleProgress getModuleProgress() { return moduleProgress; }
    public void setModuleProgress(StudentModuleProgress moduleProgress) { this.moduleProgress = moduleProgress; }

    public ModuleSection getSection() { return section; }
    public void setSection(ModuleSection section) { this.section = section; }

    public ProgressStatus getStatus() { return status; }
    public void setStatus(ProgressStatus status) { this.status = status; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
