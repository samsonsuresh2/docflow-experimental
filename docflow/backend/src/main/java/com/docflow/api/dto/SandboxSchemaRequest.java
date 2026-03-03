package com.docflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public class SandboxSchemaRequest {

    @NotBlank
    private String adminConfigJson;

    @NotBlank
    private String validationSchemaJson;

    public String getAdminConfigJson() {
        return adminConfigJson;
    }

    public void setAdminConfigJson(String adminConfigJson) {
        this.adminConfigJson = adminConfigJson;
    }

    public String getValidationSchemaJson() {
        return validationSchemaJson;
    }

    public void setValidationSchemaJson(String validationSchemaJson) {
        this.validationSchemaJson = validationSchemaJson;
    }
}
