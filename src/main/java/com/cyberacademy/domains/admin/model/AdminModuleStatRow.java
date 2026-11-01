package com.cyberacademy.domains.admin.model;

import java.util.UUID;

/** Fila de fn_admin_module_stats. */
public interface AdminModuleStatRow {
    UUID getId();
    String getCode();
    String getTitle();
    Integer getOrderIndex();
    Long getCompletedStudents();
    Long getInProgressStudents();
    Long getFailedStudents();
    Long getStartedStudents();
}
