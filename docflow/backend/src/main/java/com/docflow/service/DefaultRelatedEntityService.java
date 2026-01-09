package com.docflow.service;

import com.docflow.api.dto.RelatedEntityColumn;
import com.docflow.api.dto.RelatedEntityResponse;
import com.docflow.reports.config.ReportProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DefaultRelatedEntityService implements RelatedEntityService {

    private final JdbcTemplate jdbcTemplate;
    private final ReportProperties reportProperties;

    public DefaultRelatedEntityService(JdbcTemplate jdbcTemplate, ReportProperties reportProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportProperties = reportProperties;
    }

    @Override
    public RelatedEntityResponse getRelatedEntity(Long documentId, String entityName) {
        ReportProperties.EntityProperties entity = resolveEntity(entityName);
        ReportProperties.JoinProperties join = entity.getJoinToDocument();
        if (join == null || !join.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "joinToDocument disabled");
        }
        String documentFkColumn = join.getDocumentFkColumn();
        String businessFkColumn = join.getBusinessFkColumn();
        if (!hasText(documentFkColumn) || !hasText(businessFkColumn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "joinToDocument columns not configured");
        }

        ReportProperties.DocumentTableProperties documentTable = reportProperties.getDocumentTable();
        String tableName = documentTable.getName();
        String internalPk = documentTable.getInternalPk();
        if (!hasText(tableName) || !hasText(internalPk)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "document table config missing");
        }

        Object joinValue = fetchJoinValue(tableName, internalPk, documentFkColumn, documentId);
        RelatedEntityResponse response = new RelatedEntityResponse();
        response.setEntityName(entity.getName());
        response.setLabel(entity.getLabel());
        if (joinValue == null || joinValue.toString().isBlank()) {
            response.setRows(List.of());
            response.setColumns(List.of());
            return response;
        }

        String sql = "SELECT * FROM " + entity.getName() + " WHERE " + businessFkColumn + " = ?";
        return jdbcTemplate.query(sql, ps -> ps.setObject(1, joinValue), rs -> mapResponse(entity, rs));
    }

    private RelatedEntityResponse mapResponse(ReportProperties.EntityProperties entity, ResultSet rs) throws SQLException {
        RelatedEntityResponse response = new RelatedEntityResponse();
        response.setEntityName(entity.getName());
        response.setLabel(entity.getLabel());
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<RelatedEntityColumn> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            columns.add(new RelatedEntityColumn(label, label));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String key = meta.getColumnLabel(i);
                row.put(key, rs.getObject(i));
            }
            rows.add(row);
        }
        response.setColumns(columns);
        response.setRows(rows);
        return response;
    }

    private Object fetchJoinValue(String tableName, String internalPk, String documentFkColumn, Long documentId) {
        String sql = "SELECT " + documentFkColumn + " FROM " + tableName + " WHERE " + internalPk + " = ?";
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, documentId), rs -> {
            if (!rs.next()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
            }
            return rs.getObject(1);
        });
    }

    private ReportProperties.EntityProperties resolveEntity(String entityName) {
        if (!hasText(entityName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityName required");
        }
        Optional<ReportProperties.EntityProperties> match = reportProperties.getEnabledEntities().stream()
            .filter(entity -> entityName.equalsIgnoreCase(entity.getName()))
            .findFirst();
        return match.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown entity"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
