package com.cyberacademy.domains.admin.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de fn_admin_students. */
public interface AdminStudentRow {
    UUID getId();
    String getFirstName();
    String getLastName();
    String getEmail();
    String getStatus();
    Instant getCreatedAt();
    Long getModulesCompleted();
    Long getModulesTotal();
    BigDecimal getTotalScore();
    Long getGradedModules();
    BigDecimal getAverageScore();
    BigDecimal getProgressPercent();
    Instant getLastActivityAt();
}
