package com.docflow.service;

public record UploadSchemaStatusView(
    SchemaBindingStrategy bindingStrategy,
    Integer activeVersion,
    Integer sandboxVersion,
    String configJson,
    String updatedBy,
    String updatedAt
) {
}
