package com.cyberacademy.module.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sección de un módulo (tabla "module_sections").
 * Cada módulo tiene exactamente 3 secciones THEORY y 1 PRACTICE.
 */
@Entity
@Table(name = "module_sections")
public class ModuleSection {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM) // ENUM nativo de PostgreSQL (section_type)
    @Column(name = "section_type", nullable = false)
    private SectionType sectionType;

    @Column(name = "section_order", nullable = false)
    private int sectionOrder;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent;

    /** Texto de lectura de la teoría o instrucciones de la práctica. */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ModuleSection() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CourseModule getModule() { return module; }
    public void setModule(CourseModule module) { this.module = module; }

    public SectionType getSectionType() { return sectionType; }
    public void setSectionType(SectionType sectionType) { this.sectionType = sectionType; }

    public int getSectionOrder() { return sectionOrder; }
    public void setSectionOrder(int sectionOrder) { this.sectionOrder = sectionOrder; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getWeightPercent() { return weightPercent; }
    public void setWeightPercent(BigDecimal weightPercent) { this.weightPercent = weightPercent; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
