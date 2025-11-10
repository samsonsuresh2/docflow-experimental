package com.docflow.reports.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DynamicReportExecutor {

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

        List<Map<String, Object>> rows = jdbcTemplate.query(paginatedSql, params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (DynamicReportBuilder.ColumnSelection column : report.columns()) {
                Object value = rs.getObject(column.label());
                row.put(column.displayName(), value);
            }
            return row;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("columns", report.columns().stream().map(DynamicReportBuilder.ColumnSelection::displayName).toList());
        response.put("rows", rows);
        return response;
    }
}
