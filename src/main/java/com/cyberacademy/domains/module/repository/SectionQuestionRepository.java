package com.cyberacademy.domains.module.repository;

import com.cyberacademy.domains.module.model.SectionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SectionQuestionRepository extends JpaRepository<SectionQuestion, UUID> {

    /** Proyección para contar preguntas agrupadas por sección (evita 1 count por sección). */
    interface SectionIdCount {
        UUID getSectionId();

        long getTotal();
    }

    @EntityGraph(attributePaths = "options")
    List<SectionQuestion> findBySectionIdOrderByOrderIndexAsc(UUID sectionId);

    long countBySectionId(UUID sectionId);

    /** Nº de preguntas por cada sección pedida, en una sola consulta. */
    @Query("select q.section.id as sectionId, count(q.id) as total "
            + "from SectionQuestion q where q.section.id in :sectionIds group by q.section.id")
    List<SectionIdCount> countsBySectionIds(@Param("sectionIds") Collection<UUID> sectionIds);

    /** Borra las preguntas de una sección junto con sus opciones (cascade de la entidad). */
    void deleteBySectionId(UUID sectionId);
}
