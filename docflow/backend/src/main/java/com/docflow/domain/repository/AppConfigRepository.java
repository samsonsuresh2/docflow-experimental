package com.docflow.domain.repository;

import com.docflow.domain.AppConfig;
import com.docflow.domain.UploadSchemaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findFirstByConfigKeyAndSchemaStatusIsNull(String configKey);

    Optional<AppConfig> findFirstByConfigKeyAndSchemaStatus(String configKey, UploadSchemaStatus schemaStatus);

    List<AppConfig> findByConfigKeyAndSchemaStatus(String configKey, UploadSchemaStatus schemaStatus);

    List<AppConfig> findByConfigKeyAndSchemaStatusIn(String configKey, List<UploadSchemaStatus> statuses);

    Optional<AppConfig> findFirstByConfigKeyAndSchemaVersionAndSchemaStatusIn(String configKey,
                                                                               Integer schemaVersion,
                                                                               List<UploadSchemaStatus> statuses);
}
