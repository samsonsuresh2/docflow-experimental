package com.docflow.service.schema;

import com.docflow.domain.AppConfig;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentSchemaBindingMode;
import com.docflow.domain.UploadSchemaStatus;
import com.docflow.domain.repository.AppConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class DocumentSchemaResolver {

    private final AppConfigRepository repository;
    private final SchemaBindingProperties properties;

    public DocumentSchemaResolver(AppConfigRepository repository, SchemaBindingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SchemaBinding resolveForCreate() {
        if (properties.getBindingStrategy() == SchemaBindingStrategy.SANDBOX_ONLY) {
            AppConfig sandbox = repository.findFirstByConfigKeyAndSchemaStatus(
                    UploadSchemaAdminService.UPLOAD_SCHEMA_KEY,
                    UploadSchemaStatus.SANDBOX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "SANDBOX schema is required."));
            return new SchemaBinding(DocumentSchemaBindingMode.FLOATING_SANDBOX, sandbox.getSchemaVersion());
        }

        AppConfig active = repository.findFirstByConfigKeyAndSchemaStatus(
                UploadSchemaAdminService.UPLOAD_SCHEMA_KEY,
                UploadSchemaStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "ACTIVE schema is required."));
        return new SchemaBinding(DocumentSchemaBindingMode.FIXED_VERSION, active.getSchemaVersion());
    }

    @Transactional(readOnly = true)
    public AppConfig resolveValidationSchema(DocumentParent document) {
        if (document.getSchemaBindingMode() == DocumentSchemaBindingMode.FLOATING_SANDBOX) {
            return repository.findFirstByConfigKeyAndSchemaStatus(
                    UploadSchemaAdminService.UPLOAD_SCHEMA_KEY,
                    UploadSchemaStatus.SANDBOX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "SANDBOX schema is required."));
        }

        return repository.findFirstByConfigKeyAndSchemaVersionAndSchemaStatusIn(
                UploadSchemaAdminService.UPLOAD_SCHEMA_KEY,
                document.getSchemaVersion(),
                List.of(UploadSchemaStatus.ACTIVE, UploadSchemaStatus.DEPRECATED)
            )
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Schema version " + document.getSchemaVersion() + " not found."));
    }

    public record SchemaBinding(DocumentSchemaBindingMode mode, Integer version) {}
}
