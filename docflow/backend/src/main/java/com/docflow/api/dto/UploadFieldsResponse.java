package com.docflow.api.dto;

public class UploadFieldsResponse {

    private String configJson;

    public UploadFieldsResponse() {
    }

    public UploadFieldsResponse(String configJson) {
        this.configJson = configJson;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
