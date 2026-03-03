package com.docflow.api.dto;

import com.docflow.service.SchemaBindingStrategy;

public record UploadSchemaStatusResponse(
    SchemaBindingStrategy bindingStrategy,
    Integer activeVersion,
    Integer sandboxVersion,
    String configJson,
    String updatedBy,
    String updatedAt
) {
}
