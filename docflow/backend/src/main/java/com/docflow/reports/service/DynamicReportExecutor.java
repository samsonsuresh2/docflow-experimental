package com.docflow.reports.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DynamicReportExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicReportExecutor.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DynamicReportExecutor(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> execute(DynamicReportBuilder.BuiltReport report, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        int offset = page * size;
        String paginatedSql = report.sql() + " OFFSET :__offset ROWS FETCH NEXT :__limit ROWS ONLY";
        Map<String, Object> params = new LinkedHashMap<>(report.parameters());
        params.put("__offset", offset);
        params.put("__limit", size);

        long matchedRows = fetchMatchedRowCount(report, params);
        long metadataRows = fetchMetadataRowCount(report);
        if (metadataRows == 0 && !report.metadataKeys().isEmpty()) {
            LOGGER.warn("Report metadata keys returned no rows. context={}, keys={}, table={}",
                    report.contextLabel(), report.metadataKeys(), report.metadataTable());
        }

        List<Map<String, Object>> rows = jdbcTemplate.query(paginatedSql, params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (DynamicReportBuilder.SelectColumn column : report.columns()) {
                Object value = rs.getObject(column.label());
                row.put(column.displayName(), value);
            }
            return row;
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Report execution summary. context={}, matchedRows={}, metadataRows={}, returnedRows={}",
                    report.contextLabel(), matchedRows, metadataRows, rows.size());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("columns", report.columns().stream().map(DynamicReportBuilder.SelectColumn::displayName).toList());
        response.put("rows", rows);
        return response;
    }

    private long fetchMatchedRowCount(DynamicReportBuilder.BuiltReport report, Map<String, Object> params) {
        String countSql = "SELECT COUNT(1) FROM (" + report.sql() + ") r";
        return jdbcTemplate.queryForObject(countSql, params, Long.class);
    }

    private long fetchMetadataRowCount(DynamicReportBuilder.BuiltReport report) {
        if (report.metadataKeys().isEmpty()) {
            return 0L;
        }
        String sql = "SELECT COUNT(1) FROM " + report.metadataTable()
                + " WHERE " + report.metadataKeyColumn() + " IN (:keys)";
        Map<String, Object> params = Map.of("keys", report.metadataKeys());
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }
}
