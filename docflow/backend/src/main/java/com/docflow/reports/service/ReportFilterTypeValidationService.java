package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.service.ConfigService;
import com.docflow.service.form.UploadFieldConfigParser;
import com.docflow.service.form.UploadFieldDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReportFilterTypeValidationService {

    public static final String INVALID_FILTER_CONFIGURATION_MESSAGE =
            "This report has an invalid filter configuration. Please contact your administrator.";

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportFilterTypeValidationService.class);

    private final ReportMetadataService metadataService;
    private final ReportProperties properties;
    private final ConfigService configService;
    private final UploadFieldConfigParser uploadFieldConfigParser;

    public ReportFilterTypeValidationService(ReportMetadataService metadataService,
                                             ReportProperties properties,
                                             ConfigService configService,
                                             ObjectMapper objectMapper) {
        this.metadataService = metadataService;
        this.properties = properties;
        this.configService = configService;
        this.uploadFieldConfigParser = new UploadFieldConfigParser(objectMapper);
    }

    public void validateTemplateDefinition(DynamicReportRequest request) {
        Optional<TypeMismatch> mismatch = findFirstMismatch(request);
        if (mismatch.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mismatch.get().adminMessage());
        }
    }

    public void validateRuntimeDefinition(DynamicReportRequest request, String contextLabel) {
        try {
            Optional<TypeMismatch> mismatch = findFirstMismatch(request);
            if (mismatch.isPresent()) {
                TypeMismatch details = mismatch.get();
                LOGGER.error(
                        "Invalid report filter configuration detected before execution. context={}, filterKey={}, configuredType={}, source={}, actualType={}",
                        contextLabel,
                        details.filterKey(),
                        details.configuredType(),
                        details.sourceDescription(),
                        details.actualType()
                );
                throw new ResponseStatusException(HttpStatus.CONFLICT, INVALID_FILTER_CONFIGURATION_MESSAGE);
            }
        } catch (ResponseStatusException ex) {
            if (HttpStatus.CONFLICT.equals(ex.getStatusCode())) {
                throw ex;
            }
            LOGGER.error("Unable to validate report filter configuration at runtime. context={}", contextLabel, ex);
            throw new ResponseStatusException(HttpStatus.CONFLICT, INVALID_FILTER_CONFIGURATION_MESSAGE);
        }
    }

    private Optional<TypeMismatch> findFirstMismatch(DynamicReportRequest request) {
        if (request == null || request.getFilters() == null || request.getFilters().isEmpty()) {
            return Optional.empty();
        }
        String baseEntity = normalizeEntity(request.getBaseEntity(), properties.getDocumentTable().getName());
        for (ReportFilter filter : request.getFilters()) {
            if (filter == null || !StringUtils.hasText(filter.getKey())) {
                continue;
            }
            ReportFilter.FilterLogicalType configuredType = resolveConfiguredType(filter);
            if (configuredType == null) {
                continue;
            }
            ResolvedFieldType actualType = resolveActualFieldType(baseEntity, filter.getKey());
            if (!actualType.isCompatibleWith(configuredType)) {
                return Optional.of(new TypeMismatch(
                        filter.getKey().trim(),
                        configuredType.name(),
                        actualType.sourceDescription(),
                        actualType.actualType(),
                        actualType.expectedLogicalType().name()
                ));
            }
        }
        return Optional.empty();
    }

    private ResolvedFieldType resolveActualFieldType(String baseEntity, String key) {
        String trimmedKey = key.trim();
        if (trimmedKey.regionMatches(true, 0, "meta:", 0, 5)) {
            String metadataKey = trimmedKey.substring(5).trim();
            Map<String, String> metadataFieldTypes = metadataFieldTypes();
            String configuredFieldType = metadataFieldTypes.get(metadataKey.toUpperCase(Locale.ROOT));
            if (StringUtils.hasText(configuredFieldType)) {
                return new ResolvedFieldType(
                        mapConfiguredFieldType(configuredFieldType),
                        configuredFieldType.trim().toUpperCase(Locale.ROOT),
                        "metadata field '" + metadataKey + "'"
                );
            }
            String storageType = metadataService.getColumnDataType(
                    properties.getMetadataTable().getName(),
                    properties.getMetadataTable().getValueColumn()
            );
            return new ResolvedFieldType(
                    mapDatabaseType(storageType),
                    storageType,
                    "metadata field '" + metadataKey + "' backed by "
                            + properties.getMetadataTable().getName().toUpperCase(Locale.ROOT)
                            + "."
                            + properties.getMetadataTable().getValueColumn().toUpperCase(Locale.ROOT)
            );
        }

        String entity = baseEntity;
        String column = trimmedKey;
        if (trimmedKey.contains(".")) {
            String[] parts = trimmedKey.split("\\.", 2);
            entity = parts[0].trim();
            column = parts[1].trim();
        }
        String resolvedEntity = isDocumentEntity(entity)
                ? properties.getDocumentTable().getName().toUpperCase(Locale.ROOT)
                : normalizeEntity(entity, baseEntity);
        String resolvedColumn = normalizeColumn(column);
        String dataType = metadataService.getColumnDataType(resolvedEntity, resolvedColumn);
        return new ResolvedFieldType(
                mapDatabaseType(dataType),
                dataType,
                resolvedEntity + "." + resolvedColumn
        );
    }

    private Map<String, String> metadataFieldTypes() {
        String rawConfig = configService.getUploadFieldsConfig();
        if (!StringUtils.hasText(rawConfig)) {
            return Map.of();
        }
        List<UploadFieldDefinition> definitions = uploadFieldConfigParser.parse(rawConfig);
        if (definitions.isEmpty()) {
            return Map.of();
        }
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (UploadFieldDefinition definition : definitions) {
            if (definition == null || !StringUtils.hasText(definition.getName()) || !StringUtils.hasText(definition.getType())) {
                continue;
            }
            fieldTypes.put(definition.getName().trim().toUpperCase(Locale.ROOT), definition.getType().trim());
        }
        return Map.copyOf(fieldTypes);
    }

    private ReportFilter.FilterLogicalType resolveConfiguredType(ReportFilter filter) {
        if (filter.getLogicalType() != null) {
            return filter.getLogicalType();
        }
        if (!StringUtils.hasText(filter.getDataType())) {
            return null;
        }
        try {
            return ReportFilter.FilterLogicalType.valueOf(filter.getDataType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ReportFilter.FilterLogicalType mapDatabaseType(String rawType) {
        String normalized = normalizeType(rawType);
        if (normalized.startsWith("TIMESTAMP") || normalized.equals("DATE") || normalized.equals("DATETIME")) {
            return ReportFilter.FilterLogicalType.DATE;
        }
        if (normalized.equals("NUMBER")
                || normalized.equals("NUMERIC")
                || normalized.equals("DECIMAL")
                || normalized.equals("INTEGER")
                || normalized.equals("INT")
                || normalized.equals("SMALLINT")
                || normalized.equals("BIGINT")
                || normalized.equals("FLOAT")
                || normalized.equals("REAL")
                || normalized.equals("DOUBLE")
                || normalized.equals("BINARY_FLOAT")
                || normalized.equals("BINARY_DOUBLE")) {
            return ReportFilter.FilterLogicalType.NUMBER;
        }
        return ReportFilter.FilterLogicalType.STRING;
    }

    private ReportFilter.FilterLogicalType mapConfiguredFieldType(String rawType) {
        String normalized = normalizeType(rawType);
        if (normalized.equals("DATE") || normalized.equals("DATETIME") || normalized.equals("TIMESTAMP")) {
            return ReportFilter.FilterLogicalType.DATE;
        }
        if (normalized.equals("NUMBER") || normalized.equals("NUMERIC") || normalized.equals("DECIMAL") || normalized.equals("INTEGER")) {
            return ReportFilter.FilterLogicalType.NUMBER;
        }
        return ReportFilter.FilterLogicalType.STRING;
    }

    private String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "";
        }
        return rawType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isDocumentEntity(String entity) {
        String normalized = normalizeEntity(entity, properties.getDocumentTable().getName());
        return normalized.equals(properties.getDocumentTable().getName().toUpperCase(Locale.ROOT))
                || "DOCUMENT".equals(normalized)
                || "DOCUMENT_PARENT".equals(normalized);
    }

    private String normalizeEntity(String value, String fallback) {
        String candidate = StringUtils.hasText(value) ? value.trim() : Objects.toString(fallback, "");
        if (!StringUtils.hasText(candidate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseEntity is required");
        }
        String normalized = candidate.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entity name: " + value);
        }
        return normalized;
    }

    private String normalizeColumn(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter column is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filter column: " + value);
        }
        return normalized;
    }

    private record ResolvedFieldType(ReportFilter.FilterLogicalType expectedLogicalType,
                                     String actualType,
                                     String sourceDescription) {

        boolean isCompatibleWith(ReportFilter.FilterLogicalType configuredType) {
            return expectedLogicalType == configuredType;
        }
    }

    private record TypeMismatch(String filterKey,
                                String configuredType,
                                String sourceDescription,
                                String actualType,
                                String expectedType) {

        String adminMessage() {
            return "Filter '" + filterKey + "' is configured as " + configuredType
                    + " but " + sourceDescription + " uses " + actualType
                    + ". Choose logical type " + expectedType + ".";
        }
    }
}
