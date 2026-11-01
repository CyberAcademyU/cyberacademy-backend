package com.cyberacademy.domains.module.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Opción elegida por el estudiante en una pregunta de la práctica (tabla "practice_answers"). */
@Entity
@Table(name = "practice_answers")
@IdClass(PracticeAnswerId.class)
public class PracticeAnswer {

    @Id
    @Column(name = "section_progress_id", nullable = false)
    private UUID sectionProgressId;

    @Id
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private OffsetDateTime answeredAt = OffsetDateTime.now();

    public PracticeAnswer() {}

    public PracticeAnswer(UUID sectionProgressId, UUID questionId, UUID optionId) {
        this.sectionProgressId = sectionProgressId;
        this.questionId = questionId;
        this.optionId = optionId;
    }

    public UUID getSectionProgressId() { return sectionProgressId; }
    public UUID getQuestionId() { return questionId; }
    public UUID getOptionId() { return optionId; }
    public OffsetDateTime getAnsweredAt() { return answeredAt; }
}
