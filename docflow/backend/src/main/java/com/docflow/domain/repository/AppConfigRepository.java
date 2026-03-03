package com.docflow.domain.repository;

import com.docflow.domain.AppConfig;
import com.docflow.domain.SchemaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByConfigKeyAndSchemaStatus(String configKey, SchemaStatus schemaStatus);

    Optional<AppConfig> findByConfigKeyAndSchemaVersion(String configKey, Integer schemaVersion);

    Optional<AppConfig> findTopByConfigKeyAndSchemaStatusOrderBySchemaVersionDesc(String configKey, SchemaStatus schemaStatus);

    Optional<AppConfig> findTopByConfigKeyAndSchemaStatusInOrderBySchemaVersionDesc(String configKey, Iterable<SchemaStatus> statuses);

    Optional<AppConfig> findByConfigKeyAndSchemaStatusIsNull(String configKey);
}
