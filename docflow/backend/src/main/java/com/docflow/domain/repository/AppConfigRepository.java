package com.docflow.domain.repository;

import com.docflow.domain.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByConfigKey(String configKey);
}
