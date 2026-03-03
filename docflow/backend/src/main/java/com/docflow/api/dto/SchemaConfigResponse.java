package com.docflow.api.dto;

import com.docflow.domain.AppConfig;
import com.docflow.domain.UploadSchemaStatus;

public class SchemaConfigResponse {

    private UploadSchemaStatus status;
    private Integer version;
    private String adminConfigJson;
    private String validationSchemaJson;
    private java.time.OffsetDateTime updatedAt;

    public static SchemaConfigResponse from(AppConfig config) {
        if (config == null) {
            return null;
        }
        SchemaConfigResponse r = new SchemaConfigResponse();
        r.setStatus(config.getSchemaStatus());
        r.setVersion(config.getSchemaVersion());
        r.setAdminConfigJson(config.getConfigValue());
        r.setValidationSchemaJson(config.getValidationSchemaJson());
        r.setUpdatedAt(config.getUpdatedAt());
        return r;
    }

    public UploadSchemaStatus getStatus() { return status; }
    public void setStatus(UploadSchemaStatus status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getAdminConfigJson() { return adminConfigJson; }
    public void setAdminConfigJson(String adminConfigJson) { this.adminConfigJson = adminConfigJson; }
    public String getValidationSchemaJson() { return validationSchemaJson; }
    public void setValidationSchemaJson(String validationSchemaJson) { this.validationSchemaJson = validationSchemaJson; }
    public java.time.OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
