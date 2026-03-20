package com.docflow.service;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.context.RequestUser;
import com.docflow.domain.JsonConfig;
import com.docflow.domain.SchemaStatus;
import com.docflow.domain.repository.JsonConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

    private final JsonConfigRepository jsonConfigRepository;
    private final ObjectMapper objectMapper;
    private final SchemaBindingProperties schemaBindingProperties;

    public DefaultConfigService(JsonConfigRepository jsonConfigRepository,
                                ObjectMapper objectMapper,
                                SchemaBindingProperties schemaBindingProperties) {
        this.jsonConfigRepository = jsonConfigRepository;
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
        JsonConfig config = jsonConfigRepository.findByConfigKeyAndSchemaStatusIsNull(REVIEW_FILTER_CONFIG_KEY)
            .orElseGet(JsonConfig::new);
        config.setConfigKey(REVIEW_FILTER_CONFIG_KEY);
        config.setConfigValue(configJson);
        config.setUpdatedBy(requestUser.userId());
        config.setUpdatedAt(OffsetDateTime.now());
        jsonConfigRepository.save(config);
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
    public String getUploadFieldsConfigForBinding(JsonConfig binding) {
        if (binding == null) {
            return null;
        }
        if (binding.getSchemaVersion() != null && binding.getSchemaVersion() > 0) {
            return jsonConfigRepository.findByConfigKeyAndSchemaVersion(UPLOAD_CONFIG_KEY, binding.getSchemaVersion())
                .map(JsonConfig::getConfigValue)
                .orElse(null);
        }
        if (binding.getSchemaStatus() == SchemaStatus.SANDBOX || (binding.getSchemaVersion() != null && binding.getSchemaVersion() == 0)) {
            return jsonConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX)
                .map(JsonConfig::getConfigValue)
                .orElse(null);
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public JsonConfig resolveUploadSchemaForNewDocument() {
        return resolveUploadSchemaForRequest()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                "Required upload schema is not configured for current binding strategy"));
    }

    @Override
    public String saveSandboxUploadSchema(String configJson, RequestUser requestUser) {
        String normalizedConfig = validateUploadSchemaConfig(configJson);
        JsonConfig sandbox = jsonConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX)
            .orElseGet(JsonConfig::new);
        sandbox.setConfigKey(UPLOAD_CONFIG_KEY);
        sandbox.setSchemaStatus(SchemaStatus.SANDBOX);
        sandbox.setSchemaVersion(0);
        sandbox.setConfigValue(normalizedConfig);
        sandbox.setUpdatedBy(requestUser.userId());
        sandbox.setUpdatedAt(OffsetDateTime.now());
        jsonConfigRepository.save(sandbox);
        return sandbox.getConfigValue();
    }

    @Override
    public void promoteSandboxUploadSchema(RequestUser requestUser) {
        JsonConfig sandbox = jsonConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sandbox upload schema is missing"));
        releaseUploadSchemaAsActive(sandbox.getConfigValue(), requestUser);
    }

    @Override
    public void releaseActiveUploadSchema(String configJson, RequestUser requestUser) {
        if (schemaBindingProperties.getBindingStrategy() != SchemaBindingStrategy.ACTIVE_ONLY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Release flow is available only when schema binding strategy is ACTIVE_ONLY");
        }
        releaseUploadSchemaAsActive(configJson, requestUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UploadSchemaStatusView getUploadSchemaStatus() {
        Optional<JsonConfig> sandbox = jsonConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX);
        Optional<JsonConfig> active = jsonConfigRepository
            .findLatestByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE)
            .stream()
            .findFirst();
        String configJson = resolveUploadSchemaForRequest().map(JsonConfig::getConfigValue)
            .or(() -> sandbox.map(JsonConfig::getConfigValue))
            .or(() -> active.map(JsonConfig::getConfigValue))
            .orElse(null);
        Optional<JsonConfig> freshest = jsonConfigRepository
            .findByConfigKeyAndSchemaStatusInVersionOrder(UPLOAD_CONFIG_KEY,
                EnumSet.of(SchemaStatus.SANDBOX, SchemaStatus.ACTIVE, SchemaStatus.DEPRECATED))
            .stream()
            .findFirst();
        return new UploadSchemaStatusView(
            schemaBindingProperties.getBindingStrategy(),
            active.map(JsonConfig::getSchemaVersion).orElse(null),
            sandbox.map(JsonConfig::getSchemaVersion).orElse(0),
            configJson,
            freshest.map(JsonConfig::getUpdatedBy).orElse(null),
            freshest.map(c -> c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString()).orElse(null)
        );
    }

    private Optional<JsonConfig> resolveUploadSchemaForRequest() {
        if (schemaBindingProperties.getBindingStrategy() == SchemaBindingStrategy.SANDBOX_ONLY) {
            return jsonConfigRepository.findByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX);
        }
        return jsonConfigRepository.findLatestByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE)
            .stream()
            .findFirst();
    }

    private void releaseUploadSchemaAsActive(String configJson, RequestUser requestUser) {
        String normalizedConfig = validateUploadSchemaConfig(configJson);
        Optional<JsonConfig> currentActive = jsonConfigRepository
            .findLatestByConfigKeyAndSchemaStatus(UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE)
            .stream()
            .findFirst();
        int nextVersion = currentActive.map(JsonConfig::getSchemaVersion).orElse(0) + 1;
        OffsetDateTime now = OffsetDateTime.now();

        JsonConfig promoted = new JsonConfig();
        promoted.setConfigKey(UPLOAD_CONFIG_KEY);
        promoted.setSchemaStatus(SchemaStatus.ACTIVE);
        promoted.setSchemaVersion(nextVersion);
        promoted.setConfigValue(normalizedConfig);
        promoted.setUpdatedBy(requestUser.userId());
        promoted.setUpdatedAt(now);
        jsonConfigRepository.save(promoted);

        currentActive.ifPresent(active -> {
            active.setSchemaStatus(SchemaStatus.DEPRECATED);
            active.setUpdatedBy(requestUser.userId());
            active.setUpdatedAt(now);
            jsonConfigRepository.save(active);
        });
    }

    private String validateUploadSchemaConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload schema JSON is required");
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            boolean supportedShape = root.isArray() || (root.isObject() && root.has("fields") && root.get("fields").isArray());
            if (!supportedShape) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload schema JSON must be an array of fields or an object containing a fields array");
            }
            return objectMapper.writeValueAsString(root);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload schema JSON must be valid JSON", ex);
        }
    }


    private Optional<String> findLegacyUploadConfig() {
        return findConfigValue(UPLOAD_CONFIG_KEY)
            .or(() -> findConfigValue(LEGACY_UPLOAD_FIELDS_KEY));
    }

    private Optional<String> findConfigValue(String key) {
        return jsonConfigRepository.findByConfigKeyAndSchemaStatusIsNull(key)
            .map(JsonConfig::getConfigValue);
    }
}
