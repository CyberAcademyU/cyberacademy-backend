package com.cyberacademy.domains.module.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de saved_modules (usuario + módulo). */
public class SavedModuleId implements Serializable {

    private UUID userId;
    private UUID moduleId;

    public SavedModuleId() {}

    public SavedModuleId(UUID userId, UUID moduleId) {
        this.userId = userId;
        this.moduleId = moduleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavedModuleId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(moduleId, other.moduleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, moduleId);
    }
}
