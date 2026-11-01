package com.cyberacademy.domains.module.repository;

import com.cyberacademy.domains.module.model.SectionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SectionQuestionRepository extends JpaRepository<SectionQuestion, UUID> {

    @EntityGraph(attributePaths = "options")
    List<SectionQuestion> findBySectionIdOrderByOrderIndexAsc(UUID sectionId);

    long countBySectionId(UUID sectionId);

    /** Borra las preguntas de una sección junto con sus opciones (cascade de la entidad). */
    void deleteBySectionId(UUID sectionId);
}
