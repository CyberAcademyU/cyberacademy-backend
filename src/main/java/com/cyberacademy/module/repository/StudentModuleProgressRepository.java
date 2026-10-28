package com.cyberacademy.module.repository;

import com.cyberacademy.module.entity.StudentModuleProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentModuleProgressRepository extends JpaRepository<StudentModuleProgress, UUID> {

    Optional<StudentModuleProgress> findByUserIdAndModuleId(UUID userId, UUID moduleId);

    List<StudentModuleProgress> findByUserId(UUID userId);
}
