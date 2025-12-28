package com.docflow.domain.repository;

import com.docflow.domain.UserRoleMap;
import com.docflow.domain.UserRoleMapKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleMapRepository extends JpaRepository<UserRoleMap, UserRoleMapKey> {

    @Query("select r.role from UserRoleMap r where upper(r.userId) = upper(:userId) and r.enabled = 'Y'")
    List<String> findEnabledRoles(@Param("userId") String userId);
}
