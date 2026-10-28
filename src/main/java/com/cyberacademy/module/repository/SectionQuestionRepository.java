package com.cyberacademy.module.repository;

import com.cyberacademy.module.entity.SectionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SectionQuestionRepository extends JpaRepository<SectionQuestion, UUID> {

    List<SectionQuestion> findBySectionIdOrderByOrderIndexAsc(UUID sectionId);

    long countBySectionId(UUID sectionId);

    /** Borra las preguntas de una sección; las opciones caen por ON DELETE CASCADE. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SectionQuestion q where q.section.id = :sectionId")
    void deleteBySectionId(@Param("sectionId") UUID sectionId);
}
