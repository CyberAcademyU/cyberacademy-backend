package com.cyberacademy.module.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Opción de respuesta de una pregunta (tabla "question_options").
 * Exactamente una opción por pregunta está marcada como correcta.
 */
@Entity
@Table(name = "question_options")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private SectionQuestion question;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public QuestionOption() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SectionQuestion getQuestion() { return question; }
    public void setQuestion(SectionQuestion question) { this.question = question; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
