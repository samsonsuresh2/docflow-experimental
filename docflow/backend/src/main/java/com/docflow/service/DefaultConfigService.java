package com.docflow.service;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.context.RequestUser;
import com.docflow.domain.AppConfig;
import com.docflow.domain.SchemaStatus;
import com.docflow.domain.repository.AppConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DefaultConfigService implements ConfigService {

    public static final String UPLOAD_CONFIG_KEY = "UPLOAD_CONFIG";
    public static final String LEGACY_UPLOAD_FIELDS_KEY = "UPLOAD_FIELDS";
    public static final String REVIEW_FILTER_CONFIG_KEY = "REVIEW_FILTER_CONFIG";

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;
    private final SchemaBindingProperties schemaBindingProperties;

    public DefaultConfigService(AppConfigRepository appConfigRepository,
                                ObjectMapper objectMapper,
                                SchemaBindingProperties schemaBindingProperties) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
        this.schemaBindingProperties = schemaBindingProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public String getUploadFieldsConfig() {
        return resolveUploadSchemaForRequest()
            .map(this::getUploadFieldsConfigForBinding)
            .or(() -> findLegacyUploadConfig())
            .orElse(null);
    }

    @Override
    public String upsertUploadFieldsConfig(String configJson, RequestUser requestUser) {
        return saveSandboxUploadSchema(configJson, requestUser);
    }

    @Override
    @Transactional(readOnly = true)
    public String getReviewFilterConfig() {
        return findConfigValue(REVIEW_FILTER_CONFIG_KEY).orElse(null);
    }

    @Override
    public String upsertReviewFilterConfig(String configJson, RequestUser requestUser) {
        AppConfig config = appConfigRepository.findByConfigKeyAndSchemaStatusIsNull(REVIEW_FILTER_CONFIG_KEY)
            .orElseGet(AppConfig::new);
        config.setConfigKey(REVIEW_FILTER_CONFIG_KEY);
        config.setConfigValue(configJson);
        config.setUpdatedBy(requestUser.userId());
        config.setUpdatedAt(OffsetDateTime.now());
        appConfigRepository.save(config);
        return config.getConfigValue();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FilterDefinition> getReviewFilterDefinitions() {
        String rawConfig = getReviewFilterConfig();
        if (rawConfig == null || rawConfig.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawConfig, new TypeReference<List<FilterDefinition>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getUploadFieldsConfigForBinding(AppConfig binding) {
        if (binding == null) {
            return null;
        }
        if (binding.getSchemaVersion() != null && binding.getSchemaVersion() > 0) {
            return appConfigRepository.findByConfigKeyAndSchemaVersion(UPLOAD_CONFIG_KEY, binding.getSchemaVersion())
                .map(AppConfig::getConfigValue)
                .orElse(null);
        }
        if (binding.getSchemaStatus() == SchemaStatus.SANDBOX || (binding.getSchemaVersion() != null && binding.getSchemaVersion() == 0)) {
            return appConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX)
                .map(AppConfig::getConfigValue)
                .orElse(null);
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public AppConfig resolveUploadSchemaForNewDocument() {
        return resolveUploadSchemaForRequest()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                "Required upload schema is not configured for current binding strategy"));
    }

    @Override
    public String saveSandboxUploadSchema(String configJson, RequestUser requestUser) {
        AppConfig sandbox = appConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX)
            .orElseGet(AppConfig::new);
        sandbox.setConfigKey(UPLOAD_CONFIG_KEY);
        sandbox.setSchemaStatus(SchemaStatus.SANDBOX);
        sandbox.setSchemaVersion(0);
        sandbox.setConfigValue(configJson);
        sandbox.setUpdatedBy(requestUser.userId());
        sandbox.setUpdatedAt(OffsetDateTime.now());
        appConfigRepository.save(sandbox);
        return sandbox.getConfigValue();
    }

    @Override
    public void promoteSandboxUploadSchema(RequestUser requestUser) {
        AppConfig sandbox = appConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sandbox upload schema is missing"));

        Optional<AppConfig> currentActive = appConfigRepository
            .findTopByConfigKeyAndSchemaStatusOrderBySchemaVersionDesc(UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE);
        int nextVersion = currentActive.map(AppConfig::getSchemaVersion).orElse(0) + 1;

        AppConfig promoted = new AppConfig();
        promoted.setConfigKey(UPLOAD_CONFIG_KEY);
        promoted.setSchemaStatus(SchemaStatus.ACTIVE);
        promoted.setSchemaVersion(nextVersion);
        promoted.setConfigValue(sandbox.getConfigValue());
        promoted.setUpdatedBy(requestUser.userId());
        promoted.setUpdatedAt(OffsetDateTime.now());
        appConfigRepository.save(promoted);

        currentActive.ifPresent(active -> {
            active.setSchemaStatus(SchemaStatus.DEPRECATED);
            active.setUpdatedBy(requestUser.userId());
            active.setUpdatedAt(OffsetDateTime.now());
            appConfigRepository.save(active);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public UploadSchemaStatusView getUploadSchemaStatus() {
        Optional<AppConfig> sandbox = appConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX);
        Optional<AppConfig> active = appConfigRepository
            .findTopByConfigKeyAndSchemaStatusOrderBySchemaVersionDesc(UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE);
        String configJson = resolveUploadSchemaForRequest().map(AppConfig::getConfigValue)
            .or(() -> sandbox.map(AppConfig::getConfigValue))
            .or(() -> active.map(AppConfig::getConfigValue))
            .orElse(null);
        Optional<AppConfig> freshest = appConfigRepository
            .findTopByConfigKeyAndSchemaStatusInOrderBySchemaVersionDesc(UPLOAD_CONFIG_KEY,
                EnumSet.of(SchemaStatus.SANDBOX, SchemaStatus.ACTIVE, SchemaStatus.DEPRECATED));
        return new UploadSchemaStatusView(
            schemaBindingProperties.getBindingStrategy(),
            active.map(AppConfig::getSchemaVersion).orElse(null),
            sandbox.map(AppConfig::getSchemaVersion).orElse(0),
            configJson,
            freshest.map(AppConfig::getUpdatedBy).orElse(null),
            freshest.map(c -> c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString()).orElse(null)
        );
    }

    private Optional<AppConfig> resolveUploadSchemaForRequest() {
        if (schemaBindingProperties.getBindingStrategy() == SchemaBindingStrategy.SANDBOX_ONLY) {
            return appConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX);
        }
        return appConfigRepository.findTopByConfigKeyAndSchemaStatusOrderBySchemaVersionDesc(UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE);
    }


    private Optional<String> findLegacyUploadConfig() {
        return findConfigValue(UPLOAD_CONFIG_KEY)
            .or(() -> findConfigValue(LEGACY_UPLOAD_FIELDS_KEY));
    }

    private Optional<String> findConfigValue(String key) {
        return appConfigRepository.findByConfigKeyAndSchemaStatusIsNull(key)
            .map(AppConfig::getConfigValue);
    }
}
