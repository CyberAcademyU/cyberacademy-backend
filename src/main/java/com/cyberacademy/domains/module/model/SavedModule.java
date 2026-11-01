package com.cyberacademy.domains.module.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Módulo guardado por un estudiante (tabla "saved_modules"). */
@Entity
@Table(name = "saved_modules")
@IdClass(SavedModuleId.class)
public class SavedModule {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public SavedModule() {}

    public SavedModule(UUID userId, UUID moduleId) {
        this.userId = userId;
        this.moduleId = moduleId;
    }

    public UUID getUserId() { return userId; }
    public UUID getModuleId() { return moduleId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
