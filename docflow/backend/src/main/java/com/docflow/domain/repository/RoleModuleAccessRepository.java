package com.docflow.domain.repository;

import com.docflow.domain.RoleModuleAccess;
import com.docflow.domain.RoleModuleAccessKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleModuleAccessRepository extends JpaRepository<RoleModuleAccess, RoleModuleAccessKey> {

    @Query("select r.moduleCode from RoleModuleAccess r where upper(r.role) = upper(:role) and r.enabled = 'Y'")
    List<String> findEnabledModules(@Param("role") String role);
}
