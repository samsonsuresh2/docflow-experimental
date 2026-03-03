package com.docflow.api.dto;

public class SchemaAdminSnapshotResponse {

    private String bindingStrategy;
    private SchemaConfigResponse active;
    private SchemaConfigResponse sandbox;

    public SchemaAdminSnapshotResponse() {
    }

    public SchemaAdminSnapshotResponse(String bindingStrategy, SchemaConfigResponse active, SchemaConfigResponse sandbox) {
        this.bindingStrategy = bindingStrategy;
        this.active = active;
        this.sandbox = sandbox;
    }

    public String getBindingStrategy() { return bindingStrategy; }
    public void setBindingStrategy(String bindingStrategy) { this.bindingStrategy = bindingStrategy; }
    public SchemaConfigResponse getActive() { return active; }
    public void setActive(SchemaConfigResponse active) { this.active = active; }
    public SchemaConfigResponse getSandbox() { return sandbox; }
    public void setSandbox(SchemaConfigResponse sandbox) { this.sandbox = sandbox; }
}
