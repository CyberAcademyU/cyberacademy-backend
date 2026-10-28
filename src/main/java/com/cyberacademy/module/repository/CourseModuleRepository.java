package com.cyberacademy.module.repository;

import com.cyberacademy.module.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {

    List<CourseModule> findByActiveTrueOrderByOrderIndexAsc();

    List<CourseModule> findAllByOrderByOrderIndexAsc();

    Optional<CourseModule> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
