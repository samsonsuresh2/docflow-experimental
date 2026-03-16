package com.docflow.domain.repository;

import com.docflow.domain.AppConfig;
import com.docflow.domain.SchemaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByConfigKeyAndSchemaStatus(String configKey, SchemaStatus schemaStatus);

    Optional<AppConfig> findByConfigKeyAndSchemaVersion(String configKey, Integer schemaVersion);

    @Query("""
        SELECT ac
        FROM AppConfig ac
        WHERE ac.configKey = :configKey
          AND ac.schemaStatus = :schemaStatus
        ORDER BY COALESCE(ac.schemaVersion, 0) DESC, ac.id DESC
        """)
    List<AppConfig> findLatestByConfigKeyAndSchemaStatus(@Param("configKey") String configKey,
                                                         @Param("schemaStatus") SchemaStatus schemaStatus);

    @Query("""
        SELECT ac
        FROM AppConfig ac
        WHERE ac.configKey = :configKey
          AND ac.schemaStatus IN :statuses
        ORDER BY COALESCE(ac.schemaVersion, 0) DESC, ac.id DESC
        """)
    List<AppConfig> findByConfigKeyAndSchemaStatusInVersionOrder(@Param("configKey") String configKey,
                                                                 @Param("statuses") Collection<SchemaStatus> statuses);

    Optional<AppConfig> findByConfigKeyAndSchemaStatusIsNull(String configKey);
}
