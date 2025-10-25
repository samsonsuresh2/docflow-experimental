package com.docflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadFieldsRequest {

    @NotBlank
    private String configJson;

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
