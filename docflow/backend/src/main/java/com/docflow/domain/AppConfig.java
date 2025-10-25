package com.docflow.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_config")
@SequenceGenerator(name = "app_config_seq", sequenceName = "app_config_seq", allocationSize = 1)
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_config_seq")
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;

    @Lob
    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
