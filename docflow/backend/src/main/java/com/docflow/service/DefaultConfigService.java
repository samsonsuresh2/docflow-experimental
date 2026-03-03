package com.docflow.service;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.context.RequestUser;
import com.docflow.domain.AppConfig;
import com.docflow.domain.UploadSchemaStatus;
import com.docflow.domain.repository.AppConfigRepository;
import com.docflow.service.schema.UploadSchemaAdminService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
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
    private final UploadSchemaAdminService uploadSchemaAdminService;

    public DefaultConfigService(AppConfigRepository appConfigRepository,
                                ObjectMapper objectMapper,
                                UploadSchemaAdminService uploadSchemaAdminService) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
        this.uploadSchemaAdminService = uploadSchemaAdminService;
    }

    @Override
    @Transactional(readOnly = true)
    public String getUploadFieldsConfig() {
        return appConfigRepository.findFirstByConfigKeyAndSchemaStatus(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX)
            .map(AppConfig::getConfigValue)
            .or(() -> findConfigValue(UPLOAD_CONFIG_KEY))
            .or(() -> findConfigValue(LEGACY_UPLOAD_FIELDS_KEY))
            .orElse(null);
    }

    @Override
    public String upsertUploadFieldsConfig(String configJson, RequestUser requestUser) {
        uploadSchemaAdminService.saveSandbox(configJson, configJson, requestUser);
        return configJson;
    }

    @Override
    @Transactional(readOnly = true)
    public String getReviewFilterConfig() {
        return appConfigRepository.findFirstByConfigKeyAndSchemaStatusIsNull(REVIEW_FILTER_CONFIG_KEY)
            .map(AppConfig::getConfigValue)
            .orElse(null);
    }

    @Override
    public String upsertReviewFilterConfig(String configJson, RequestUser requestUser) {
        AppConfig config = appConfigRepository.findFirstByConfigKeyAndSchemaStatusIsNull(REVIEW_FILTER_CONFIG_KEY)
                .orElseGet(AppConfig::new);
        config.setConfigKey(REVIEW_FILTER_CONFIG_KEY);
        config.setSchemaStatus(null);
        config.setSchemaVersion(null);
        config.setConfigValue(configJson);
        config.setValidationSchemaJson(null);
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

    private Optional<String> findConfigValue(String key) {
        return appConfigRepository.findFirstByConfigKeyAndSchemaStatusIsNull(key)
                .map(AppConfig::getConfigValue);
    }
}
