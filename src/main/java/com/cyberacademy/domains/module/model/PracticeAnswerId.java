package com.cyberacademy.domains.module.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de practice_answers (progreso de la práctica + pregunta). */
public class PracticeAnswerId implements Serializable {

    private UUID sectionProgressId;
    private UUID questionId;

    public PracticeAnswerId() {}

    public PracticeAnswerId(UUID sectionProgressId, UUID questionId) {
        this.sectionProgressId = sectionProgressId;
        this.questionId = questionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PracticeAnswerId other)) return false;
        return Objects.equals(sectionProgressId, other.sectionProgressId)
                && Objects.equals(questionId, other.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sectionProgressId, questionId);
    }
}
