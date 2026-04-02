package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportMetadataService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;
    private final ReportProperties properties;
    private final Map<String, CachedEntityMetadata> entityCache = new ConcurrentHashMap<>();
    private volatile CachedMetadataKeys cachedMetadataKeys;

    public ReportMetadataService(JdbcTemplate jdbcTemplate, ReportProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<BaseEntity> listBaseEntities() {
        List<ReportProperties.EntityProperties> configured = properties.getEnabledEntities();
        List<BaseEntity> entities = configured.stream()
                .map(e -> new BaseEntity(normalize(e.getName()), Objects.toString(e.getLabel(), e.getName()), e.getType(), true, e.getJoinToDocument().getBusinessFkColumn()))
                .toList();

        BaseEntity documentParent = new BaseEntity(
                normalize(properties.getDocumentTable().getName()),
                "Document",
                "RELATIONAL",
                false,
                null
        );
        List<BaseEntity> result = new java.util.ArrayList<>();
        result.add(documentParent);
        result.addAll(entities);
        return Collections.unmodifiableList(result);
    }

    public EntityColumns getColumns(String entity) {
        return getEntityMetadata(entity).columns();
    }

    public String getColumnDataType(String entity, String column) {
        EntityMetadata metadata = getEntityMetadata(entity);
        String normalizedColumn = normalizeColumn(column);
        String dataType = metadata.dataTypes().get(normalizedColumn);
        if (!StringUtils.hasText(dataType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown column " + column + " on " + metadata.entity());
        }
        return dataType;
    }

    public List<String> listMetadataKeys() {
        long now = System.currentTimeMillis();
        CachedMetadataKeys cached = cachedMetadataKeys;
        if (cached != null && cached.expiresAt() > now) {
            return cached.keys();
        }
        List<String> keys = fetchMetadataKeys();
        cachedMetadataKeys = new CachedMetadataKeys(keys, now + CACHE_TTL.toMillis());
        return keys;
    }

    private EntityMetadata getEntityMetadata(String entity) {
        String normalized = normalize(entity);
        long now = System.currentTimeMillis();
        CachedEntityMetadata cached = entityCache.get(normalized);
        if (cached != null && cached.expiresAt() > now) {
            return cached.metadata();
        }
        EntityMetadata metadata = fetchEntityMetadata(normalized);
        entityCache.put(normalized, new CachedEntityMetadata(metadata, now + CACHE_TTL.toMillis()));
        return metadata;
    }

    private EntityMetadata fetchEntityMetadata(String entity) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?";
        try {
            List<ColumnDescriptor> rows = jdbcTemplate.query(
                    sql,
                    ps -> ps.setString(1, entity),
                    (rs, rowNum) -> new ColumnDescriptor(rs.getString("COLUMN_NAME"), rs.getString("DATA_TYPE"))
            );
            if (rows == null || rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity: " + entity);
            }
            List<String> columns = new ArrayList<>();
            Map<String, String> dataTypes = new LinkedHashMap<>();
            for (ColumnDescriptor row : rows) {
                if (row == null || !StringUtils.hasText(row.columnName())) {
                    continue;
                }
                String columnName = row.columnName().trim().toUpperCase(Locale.ROOT);
                columns.add(columnName);
                dataTypes.put(columnName, normalizeDataType(row.dataType()));
            }
            if (columns.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity: " + entity);
            }
            return new EntityMetadata(entity, new EntityColumns(entity, List.copyOf(columns)), Map.copyOf(dataTypes));
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to inspect entity: " + entity, ex);
        }
    }

    private List<String> fetchMetadataKeys() {
        ReportProperties.MetadataTableProperties meta = properties.getMetadataTable();
        String sql = "SELECT DISTINCT " + meta.getKeyColumn() + " FROM " + meta.getName() + " ORDER BY " + meta.getKeyColumn();
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to inspect metadata keys", ex);
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entity is required");
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.matches("[A-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entity name: " + value);
        }
        return trimmed;
    }

    private String normalizeColumn(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column is required");
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (!trimmed.matches("[A-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid column name: " + value);
        }
        return trimmed;
    }

    private String normalizeDataType(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record BaseEntity(String name, String label, String type, boolean joinsToDocument, String businessFkColumn) {
    }

    public record EntityColumns(String entity, List<String> columns) {
    }

    private record CachedEntityMetadata(EntityMetadata metadata, long expiresAt) {
    }

    private record CachedMetadataKeys(List<String> keys, long expiresAt) {
    }

    private record EntityMetadata(String entity, EntityColumns columns, Map<String, String> dataTypes) {
    }

    private record ColumnDescriptor(String columnName, String dataType) {
    }
}
