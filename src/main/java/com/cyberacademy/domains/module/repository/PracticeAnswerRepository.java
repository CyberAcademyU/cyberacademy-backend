package com.cyberacademy.domains.module.repository;

import com.cyberacademy.domains.module.model.PracticeAnswer;
import com.cyberacademy.domains.module.model.PracticeAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, PracticeAnswerId> {

    List<PracticeAnswer> findBySectionProgressId(UUID sectionProgressId);

    void deleteBySectionProgressId(UUID sectionProgressId);
}
