package com.cyberacademy.domains.module.repository;

import com.cyberacademy.domains.module.model.SavedModule;
import com.cyberacademy.domains.module.model.SavedModuleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedModuleRepository extends JpaRepository<SavedModule, SavedModuleId> {

    List<SavedModule> findByUserId(UUID userId);

    boolean existsByUserIdAndModuleId(UUID userId, UUID moduleId);

    void deleteByUserIdAndModuleId(UUID userId, UUID moduleId);
}
