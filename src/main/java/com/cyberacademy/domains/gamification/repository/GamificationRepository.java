package com.cyberacademy.domains.gamification.repository;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.gamification.model.CompletedSectionRow;
import com.cyberacademy.domains.gamification.model.LeaderboardRow;
import com.cyberacademy.domains.gamification.model.ModuleResultRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@Repository
public interface GamificationRepository extends JpaRepository<User, UUID> {

    @Query(value = "SELECT * FROM fn_leaderboard(:since)", nativeQuery = true)
    List<LeaderboardRow> leaderboard(@Param("since") OffsetDateTime since);

    @Query(value = "SELECT * FROM fn_student_completed_sections(:userId)", nativeQuery = true)
    List<CompletedSectionRow> completedSections(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM fn_student_module_results(:userId)", nativeQuery = true)
    List<ModuleResultRow> moduleResults(@Param("userId") UUID userId);
}
