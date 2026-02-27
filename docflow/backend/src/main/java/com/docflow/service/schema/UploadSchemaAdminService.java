package com.docflow.service.schema;

import com.docflow.context.RequestUser;
import com.docflow.domain.AppConfig;
import com.docflow.domain.UploadSchemaStatus;
import com.docflow.domain.repository.AppConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class UploadSchemaAdminService {

    public static final int SANDBOX_VERSION = 0;
    public static final String UPLOAD_SCHEMA_KEY = "UPLOAD_SCHEMA";

    private final AppConfigRepository repository;

    public UploadSchemaAdminService(AppConfigRepository repository) {
        this.repository = repository;
    }

    public AppConfig saveSandbox(String adminConfigJson, String validationSchemaJson, RequestUser user) {
        AppConfig sandbox = repository.findFirstByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX)
            .orElseGet(AppConfig::new);
        sandbox.setConfigKey(UPLOAD_SCHEMA_KEY);
        sandbox.setSchemaStatus(UploadSchemaStatus.SANDBOX);
        sandbox.setSchemaVersion(SANDBOX_VERSION);
        sandbox.setConfigValue(adminConfigJson);
        sandbox.setValidationSchemaJson(validationSchemaJson);
        sandbox.setUpdatedBy(user.userId());
        sandbox.setUpdatedAt(OffsetDateTime.now());
        AppConfig saved = repository.save(sandbox);
        enforceSingletons();
        return saved;
    }

    public AppConfig promoteSandboxToActive(RequestUser user) {
        AppConfig sandbox = repository.findFirstByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "SANDBOX schema does not exist."));

        AppConfig currentActive = repository.findFirstByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.ACTIVE).orElse(null);
        if (currentActive != null) {
            currentActive.setSchemaStatus(UploadSchemaStatus.DEPRECATED);
            currentActive.setUpdatedBy(user.userId());
            currentActive.setUpdatedAt(OffsetDateTime.now());
            repository.save(currentActive);
        }

        int nextVersion = currentActive == null ? 1 : currentActive.getSchemaVersion() + 1;
        AppConfig active = new AppConfig();
        active.setConfigKey(UPLOAD_SCHEMA_KEY);
        active.setSchemaStatus(UploadSchemaStatus.ACTIVE);
        active.setSchemaVersion(nextVersion);
        active.setConfigValue(sandbox.getConfigValue());
        active.setValidationSchemaJson(sandbox.getValidationSchemaJson());
        active.setUpdatedBy(user.userId());
        active.setUpdatedAt(OffsetDateTime.now());
        AppConfig saved = repository.save(active);

        enforceSingletons();
        return saved;
    }

    @Transactional(readOnly = true)
    public UploadSchemaSnapshot getCurrentSnapshot() {
        return new UploadSchemaSnapshot(
            repository.findFirstByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.ACTIVE).orElse(null),
            repository.findFirstByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX).orElse(null)
        );
    }

    private void enforceSingletons() {
        List<AppConfig> active = repository.findByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.ACTIVE);
        if (active.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only one ACTIVE schema is allowed.");
        }
        List<AppConfig> sandbox = repository.findByConfigKeyAndSchemaStatus(UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX);
        if (sandbox.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only one SANDBOX schema is allowed.");
        }
    }

    public record UploadSchemaSnapshot(AppConfig active, AppConfig sandbox) {}
}
