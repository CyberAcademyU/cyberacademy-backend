package com.cyberacademy.module.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pregunta de una sección (tabla "section_questions").
 * Las teorías tienen 1 pregunta de comprensión; la práctica, varias.
 */
@Entity
@Table(name = "section_questions")
public class SectionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private ModuleSection section;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<QuestionOption> options = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public SectionQuestion() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ModuleSection getSection() { return section; }
    public void setSection(ModuleSection section) { this.section = section; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public List<QuestionOption> getOptions() { return options; }
    public void setOptions(List<QuestionOption> options) { this.options = options; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
