package com.docflow.domain.repository;

import com.docflow.domain.RoleWorkflowActionAccess;
import com.docflow.domain.RoleWorkflowActionAccessKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleWorkflowActionAccessRepository extends JpaRepository<RoleWorkflowActionAccess, RoleWorkflowActionAccessKey> {

    @Query("select r.actionCode from RoleWorkflowActionAccess r where upper(r.roleName) = upper(:role) and upper(r.fromStatus) = upper(:status) and r.enabled = 'Y'")
    List<String> findEnabledActionCodes(@Param("role") String role, @Param("status") String status);
}
