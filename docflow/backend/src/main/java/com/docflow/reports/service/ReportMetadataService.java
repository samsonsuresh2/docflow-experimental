package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Collections;
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
    private final Map<String, CachedColumns> columnCache = new ConcurrentHashMap<>();
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
        String normalized = normalize(entity);
        long now = System.currentTimeMillis();
        CachedColumns cached = columnCache.get(normalized);
        if (cached != null && cached.expiresAt() > now) {
            return cached.columns();
        }
        EntityColumns columns = fetchColumns(normalized);
        columnCache.put(normalized, new CachedColumns(columns, now + CACHE_TTL.toMillis()));
        return columns;
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

    private EntityColumns fetchColumns(String entity) {
        String sql = "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?";
        try {
            List<String> columns = jdbcTemplate.query(sql, ps -> ps.setString(1, entity), (rs, rowNum) -> rs.getString(1));
            if (columns == null || columns.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown entity: " + entity);
            }
            List<String> normalized = columns.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(c -> c.toUpperCase(Locale.ROOT))
                    .toList();
            return new EntityColumns(entity, normalized);
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

    public record BaseEntity(String name, String label, String type, boolean joinsToDocument, String businessFkColumn) {
    }

    public record EntityColumns(String entity, List<String> columns) {
    }

    private record CachedColumns(EntityColumns columns, long expiresAt) {
    }

    private record CachedMetadataKeys(List<String> keys, long expiresAt) {
    }
}
