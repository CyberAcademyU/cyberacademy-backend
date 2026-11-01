package com.cyberacademy.domains.admin.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Fila de fn_admin_recent_activity. */
public interface AdminActivityRow {
    String getStudentName();
    String getCode();
    String getTitle();
    String getSectionTitle();
    BigDecimal getScore();
    String getStatus();
    Instant getUpdatedAt();
}
