package com.cyberacademy.domains.module.repository;

import com.cyberacademy.domains.module.model.ModuleSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuleSectionRepository extends JpaRepository<ModuleSection, UUID> {

    List<ModuleSection> findByModuleId(UUID moduleId);

    List<ModuleSection> findByModuleIdIn(Collection<UUID> moduleIds);

    Optional<ModuleSection> findByIdAndModuleId(UUID id, UUID moduleId);

    long countByModuleId(UUID moduleId);
}
