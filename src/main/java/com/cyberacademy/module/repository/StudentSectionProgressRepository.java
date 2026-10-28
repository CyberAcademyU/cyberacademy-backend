package com.cyberacademy.module.repository;

import com.cyberacademy.module.entity.StudentSectionProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentSectionProgressRepository extends JpaRepository<StudentSectionProgress, UUID> {

    Optional<StudentSectionProgress> findByModuleProgressIdAndSectionId(UUID moduleProgressId, UUID sectionId);

    List<StudentSectionProgress> findByModuleProgressId(UUID moduleProgressId);

    List<StudentSectionProgress> findByModuleProgressIdIn(Collection<UUID> moduleProgressIds);
}
