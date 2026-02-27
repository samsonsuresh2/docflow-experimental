package com.docflow.api.dto;

public class SchemaAdminSnapshotResponse {

    private SchemaConfigResponse active;
    private SchemaConfigResponse sandbox;

    public SchemaAdminSnapshotResponse() {
    }

    public SchemaAdminSnapshotResponse(SchemaConfigResponse active, SchemaConfigResponse sandbox) {
        this.active = active;
        this.sandbox = sandbox;
    }

    public SchemaConfigResponse getActive() { return active; }
    public void setActive(SchemaConfigResponse active) { this.active = active; }
    public SchemaConfigResponse getSandbox() { return sandbox; }
    public void setSandbox(SchemaConfigResponse sandbox) { this.sandbox = sandbox; }
}
