package com.docflow.reports.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportMetadataService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String RELATIONSHIP_RESOURCE = "classpath:reports/relationships.yml";

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CachedMetadata> cache = new ConcurrentHashMap<>();
    private final Map<String, List<Relationship>> relationships;
    private volatile CachedEntityList cachedEntities;

    public ReportMetadataService(JdbcTemplate jdbcTemplate, ResourceLoader resourceLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.relationships = loadRelationships(resourceLoader.getResource(RELATIONSHIP_RESOURCE));
    }

    public EntityMetadata getMetadata(String entity) {
        String table = normalizeEntity(entity);
        long now = System.currentTimeMillis();
        CachedMetadata cached = cache.get(table);
        if (cached != null && cached.expiresAt() > now) {
            return cached.metadata();
        }

        EntityMetadata metadata = fetchMetadata(table);
        cache.put(table, new CachedMetadata(metadata, now + CACHE_TTL.toMillis()));
        return metadata;
    }

    public List<String> listEntities() {
        long now = System.currentTimeMillis();
        CachedEntityList cached = cachedEntities;
        if (cached != null && cached.expiresAt() > now) {
            return cached.entities();
        }

        List<String> entities = fetchEntities();
        cachedEntities = new CachedEntityList(entities, now + CACHE_TTL.toMillis());
        return entities;
    }

    private EntityMetadata fetchMetadata(String entity) {
        List<String> keys = Collections.unmodifiableList(new ArrayList<>(queryKeys(entity)));
        List<Relationship> rels = relationships.getOrDefault(entity, List.of());
        return new EntityMetadata(entity, keys, rels);
    }

    private List<String> fetchEntities() {
        Set<String> names = new TreeSet<>();
        names.addAll(queryEntityTables());
        names.addAll(relationships.keySet());
        return List.copyOf(names);
    }

    private List<String> queryKeys(String entity) {
        String sql = "SELECT DISTINCT column_key FROM " + entity + " ORDER BY column_key";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to inspect entity: " + entity, ex);
        }
    }

    private List<String> queryEntityTables() {
        String sql = "SELECT DISTINCT LOWER(table_name) "
                + "FROM information_schema.columns "
                + "WHERE LOWER(column_name) = 'column_key'";
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String value = rs.getString(1);
                return value != null ? value.toLowerCase(Locale.ROOT) : null;
            }).stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private String normalizeEntity(String entity) {
        String value = Optional.ofNullable(entity)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entity parameter is required"));
        if (!value.matches("[a-zA-Z0-9_]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entity name: " + value);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private Map<String, List<Relationship>> loadRelationships(Resource resource) {
        if (resource == null || !resource.exists()) {
            return Map.of();
        }
        try (InputStream input = resource.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, YamlEntity> raw = mapper.readValue(input, new TypeReference<>() {
            });
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, List<Relationship>> loaded = new ConcurrentHashMap<>();
            for (Map.Entry<String, YamlEntity> entry : raw.entrySet()) {
                String key = Optional.ofNullable(entry.getKey())
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .orElse(null);
                if (key == null || !key.matches("[a-z0-9_]+")) {
                    continue;
                }

                List<Relationship> rels = new ArrayList<>();
                if (entry.getValue() != null) {
                    List<YamlRelationship> yamlRelationships = Optional
                            .ofNullable(entry.getValue().relationships())
                            .orElse(List.of());
                    for (YamlRelationship relationship : yamlRelationships) {
                        if (relationship == null) {
                            continue;
                        }
                        String to = Objects.toString(relationship.to(), null);
                        String via = Objects.toString(relationship.via(), null);
                        if (to == null || to.isBlank() || via == null || via.isBlank()) {
                            continue;
                        }
                        rels.add(new Relationship(to.trim().toLowerCase(Locale.ROOT), via.trim()));
                    }
                }
                loaded.put(key, Collections.unmodifiableList(rels));
            }
            return Collections.unmodifiableMap(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read report relationships metadata", e);
        }
    }

    private record CachedMetadata(EntityMetadata metadata, long expiresAt) {
    }

    private record CachedEntityList(List<String> entities, long expiresAt) {
    }

    public record EntityMetadata(String entity, List<String> availableKeys, List<Relationship> relationships) {
    }

    public record Relationship(String to, String via) {
    }

    private record YamlEntity(List<YamlRelationship> relationships) {
    }

    private record YamlRelationship(String to, String via) {
    }
}
