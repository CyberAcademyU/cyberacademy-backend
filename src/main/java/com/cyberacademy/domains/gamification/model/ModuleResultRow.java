package com.cyberacademy.domains.gamification.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila de fn_student_module_results. */
public interface ModuleResultRow {
    Boolean getActive();
    String getStatus();
    BigDecimal getScore();
    Instant getCompletedAt();
}
