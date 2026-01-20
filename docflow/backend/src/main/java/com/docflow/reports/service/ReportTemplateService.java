package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportTemplateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReportTemplateService {

    private static final String TABLE_NAME = "REPORT_TEMPLATES";
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTemplateService.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<ReportTemplateResponse> rowMapper = this::mapRow;

    public ReportTemplateService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ReportTemplateResponse save(String name, DynamicReportRequest request, String createdBy) {
        return createTemplate(name, request, createdBy);
    }

    public ReportTemplateResponse createTemplate(String name, DynamicReportRequest request, String createdBy) {
        String trimmedName = optionalString(name);
        if (trimmedName == null || trimmedName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template name is required");
        }

        DynamicReportRequest payload = Objects.requireNonNull(request, "request");
        String author = optionalString(createdBy);
        if (author == null || author.isEmpty()) {
            author = "system";
        }

        int filterCount = payload.getFilters() != null ? payload.getFilters().size() : 0;
        LOGGER.info("Saving report template '{}' with {} filters", trimmedName, filterCount);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(new StoredTemplatePayload(payload, author));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to serialise report request", e);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", trimmedName)
                .addValue("configJson", requestJson);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(
                    "INSERT INTO " + TABLE_NAME + " (name, config_json) "
                            + "VALUES (:name, :configJson)",
                    params,
                    keyHolder,
                    new String[]{"id"});
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateTemplateName(e)) {
                throw new DuplicateTemplateNameException("Duplicate report template name not allowed", e);
            }
            throw e;
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store report template", e);
        }

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Template identifier was not generated");
        }

        return getById(key.longValue());
    }

    public ReportTemplateResponse update(long templateId, String name, DynamicReportRequest request) {
        String trimmedName = optionalString(name);
        if (trimmedName == null || trimmedName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template name is required");
        }

        DynamicReportRequest payload = Objects.requireNonNull(request, "request");
        ReportTemplateResponse existing = getById(templateId);
        String author = optionalString(existing.getCreatedBy());
        if (author == null || author.isEmpty()) {
            author = "system";
        }

        int filterCount = payload.getFilters() != null ? payload.getFilters().size() : 0;
        LOGGER.info("Updating report template {} ('{}') with {} filters", templateId, trimmedName, filterCount);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(new StoredTemplatePayload(payload, author));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to serialise report request", e);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", templateId)
                .addValue("name", trimmedName)
                .addValue("configJson", requestJson);

        try {
            int updated = jdbcTemplate.update(
                    "UPDATE " + TABLE_NAME + " SET name = :name, config_json = :configJson WHERE id = :id",
                    params
            );
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
            }
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateTemplateName(e)) {
                throw new DuplicateTemplateNameException("Duplicate report template name not allowed", e);
            }
            throw e;
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update report template", e);
        }

        return getById(templateId);
    }

    public List<ReportTemplateResponse> listTemplates() {
        try {
            return jdbcTemplate.query(
                    "SELECT id, name, description, config_json, created_at FROM " + TABLE_NAME + " ORDER BY created_at DESC",
                    rowMapper);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load report templates", e);
        }
    }

    public ReportTemplateResponse getById(long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, name, description, config_json, created_at FROM " + TABLE_NAME + " WHERE id = :id",
                    Map.of("id", id),
                    rowMapper);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load stored template", e);
        }
    }

    private ReportTemplateResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        Timestamp createdAt = rs.getTimestamp("created_at");
        String json = rs.getString("config_json");
        StoredTemplatePayload payload = deserializePayload(json);
        Instant created = createdAt != null ? createdAt.toInstant() : Instant.now();
        int filterCount = payload.request() != null && payload.request().getFilters() != null
                ? payload.request().getFilters().size()
                : 0;
        LOGGER.info("Loaded report template {} ('{}') with {} filters", id, name, filterCount);
        return new ReportTemplateResponse(id, name, description, payload.request(), payload.createdBy(), created);
    }

    private StoredTemplatePayload deserializePayload(String json) {
        if (json == null || json.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored template is invalid");
        }
        try {
            StoredTemplatePayload payload = objectMapper.readValue(json, StoredTemplatePayload.class);
            String createdBy = optionalString(payload.createdBy());
            if (createdBy == null) {
                createdBy = "system";
            }
            return new StoredTemplatePayload(payload.request(), createdBy);
        } catch (JsonProcessingException first) {
            try {
                DynamicReportRequest request = objectMapper.readValue(json, DynamicReportRequest.class);
                return new StoredTemplatePayload(request, "system");
            } catch (JsonProcessingException second) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored template is invalid", second);
            }
        }
    }

    private String optionalString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record StoredTemplatePayload(DynamicReportRequest request, String createdBy) {
    }

    private boolean isDuplicateTemplateName(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        if (normalized.contains("report_templates_name_uk")) {
            return true;
        }
        return normalized.contains("report_templates")
                && normalized.contains("name")
                && (normalized.contains("unique") || normalized.contains("constraint") || normalized.contains("ora-00001"));
    }
}
