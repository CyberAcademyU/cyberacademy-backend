package com.cyberacademy.domains.gamification.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila de fn_student_completed_sections. */
public interface CompletedSectionRow {
    Boolean getTheory();
    BigDecimal getScore();
    Instant getDoneAt();
    Integer getAttempts();
}
