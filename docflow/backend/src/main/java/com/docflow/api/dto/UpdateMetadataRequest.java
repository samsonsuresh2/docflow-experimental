package com.docflow.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class UpdateMetadataRequest {

    @NotNull
    private Map<String, Object> metadata;

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
