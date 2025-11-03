package com.docflow.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "excel")
public class DataInjectorProperties {

    private String targetTable;
    private String primaryKey;
    private Map<String, String> mappings = new LinkedHashMap<>();
    private Map<String, String> extractors = new LinkedHashMap<>();

    private final Map<String, String> normalizedMappingKeys = new ConcurrentHashMap<>();
    private final Map<String, String> normalizedExtractors = new ConcurrentHashMap<>();

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> mappings) {
        this.mappings = mappings != null ? new LinkedHashMap<>(mappings) : new LinkedHashMap<>();
        normalizedMappingKeys.clear();
        if (this.mappings != null) {
            this.mappings.forEach((key, value) -> normalizedMappingKeys.put(normalize(key), value));
        }
    }

    public Map<String, String> getExtractors() {
        return extractors;
    }

    public void setExtractors(Map<String, String> extractors) {
        this.extractors = extractors != null ? new LinkedHashMap<>(extractors) : new LinkedHashMap<>();
        normalizedExtractors.clear();
        if (this.extractors != null) {
            this.extractors.forEach((key, value) -> normalizedExtractors.put(normalize(key), value));
        }
    }

    public Optional<String> findMapping(String headerName) {
        if (headerName == null) {
            return Optional.empty();
        }
        String normalized = normalize(headerName);
        if (normalizedMappingKeys.containsKey(normalized)) {
            return Optional.ofNullable(normalizedMappingKeys.get(normalized));
        }
        return Optional.empty();
    }

    public Optional<String> findExtractor(String column) {
        if (column == null) {
            return Optional.empty();
        }
        String normalized = normalize(column);
        if (normalizedExtractors.containsKey(normalized)) {
            return Optional.ofNullable(normalizedExtractors.get(normalized));
        }
        return Optional.empty();
    }

    public Set<String> knownColumns() {
        Map<String, String> safeMappings = mappings != null ? mappings : Collections.emptyMap();
        return safeMappings.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
