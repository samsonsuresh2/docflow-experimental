package com.docflow.reports.service;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docflow.reports.util.JsonSafeValueConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        int offset = page * size;
        Map<String, Object> params = new LinkedHashMap<>(report.parameters());
        params.put("__offset", offset);
        params.put("__limit", size);

        try {
            String paginatedSql = paginatedSql(report);
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
                    row.put(column.displayName(), JsonSafeValueConverter.convert(value));
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
            response.put("rowCount", matchedRows);
            return response;
        } catch (DataAccessException ex) {
            if (isFilterTypeMismatch(ex)) {
                LOGGER.error("Report execution failed due to invalid filter configuration. context={}, sql={}, params={}",
                        report.contextLabel(), report.sql(), report.parameters(), ex);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        ReportFilterTypeValidationService.INVALID_FILTER_CONFIGURATION_MESSAGE);
            }
            throw ex;
        }
    }

    private String paginatedSql(DynamicReportBuilder.BuiltReport report) {
        String orderBy = report.columns().isEmpty()
                ? "1"
                : "r." + report.columns().get(0).label();
        return "SELECT * FROM (" + report.sql() + ") r ORDER BY " + orderBy + " OFFSET :__offset ROWS FETCH NEXT :__limit ROWS ONLY";
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

    private boolean isFilterTypeMismatch(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("invalid number")
                        || normalized.contains("not a valid month")
                        || normalized.contains("literal does not match format string")
                        || normalized.contains("to_number")
                        || normalized.contains("to_date")
                        || normalized.contains("data conversion error")
                        || normalized.contains("error converting data type")
                        || normalized.contains("cannot convert")
                        || normalized.contains("cannot parse")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
