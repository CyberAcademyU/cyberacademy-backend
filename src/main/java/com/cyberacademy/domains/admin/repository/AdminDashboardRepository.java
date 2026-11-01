package com.cyberacademy.domains.admin.repository;

import com.cyberacademy.domains.admin.model.AdminActivityRow;
import com.cyberacademy.domains.admin.model.AdminModuleStatRow;
import com.cyberacademy.domains.admin.model.AdminStudentRow;
import com.cyberacademy.domains.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


@Repository
public interface AdminDashboardRepository extends JpaRepository<User, UUID> {

    @Query(value = "SELECT * FROM fn_admin_students()", nativeQuery = true)
    List<AdminStudentRow> students();

    @Query(value = "SELECT * FROM fn_admin_module_stats()", nativeQuery = true)
    List<AdminModuleStatRow> moduleStats();

    @Query(value = "SELECT * FROM fn_admin_recent_activity(:limit)", nativeQuery = true)
    List<AdminActivityRow> recentActivity(@Param("limit") int limit);
}
