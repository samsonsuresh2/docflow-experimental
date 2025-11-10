package com.docflow.service;

import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.context.RequestUser;
import com.docflow.service.config.DataInjectorProperties;
import com.docflow.service.config.ExcelHeaderMappingResolver;
import com.docflow.service.config.ExcelHeaderMappingResolver.ColumnBinding;
import com.docflow.service.config.ExcelHeaderMappingResolver.HeaderDescriptor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class DefaultDataInjectorService implements DataInjectorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDataInjectorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataInjectorProperties properties;
    private final ExcelHeaderMappingResolver headerMappingResolver;

    public DefaultDataInjectorService(JdbcTemplate jdbcTemplate,
                                      DataInjectorProperties properties,
                                      ExcelHeaderMappingResolver headerMappingResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.headerMappingResolver = headerMappingResolver;
    }

    @Override
    public DataInjectorResponse uploadExcel(MultipartFile file, RequestUser user) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must contain data");
        }

        String targetTable = sqlTableName(properties.getTargetTable());
        String configuredPrimaryKey = requirePrimaryKey();
        String primaryKeyColumn = sqlColumnName(configuredPrimaryKey);

        LOGGER.info("Starting data injector upload for file: {} (target table: {}, primary key: {})",
            file.getOriginalFilename(), targetTable, primaryKeyColumn);

        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Uploaded Excel file does not contain any sheets");
            }

            HeaderDescriptor headerDescriptor = headerMappingResolver.resolve(sheet.getRow(0));
            if (!headerDescriptor.hasColumn(configuredPrimaryKey)) {
                throw new IllegalArgumentException(
                    "Header must include a column mapped to primary key: " + configuredPrimaryKey);
            }

            DataInjectorResponse response = new DataInjectorResponse();
            response.setIgnoredColumns(new ArrayList<>(headerDescriptor.ignoredHeaders()));

            int totalRows = 0;
            int inserted = 0;
            int updated = 0;
            int skipped = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                RowPayload payload = readRowPayload(row, headerDescriptor.mappedColumns());
                if (!payload.hasValues()) {
                    continue;
                }

                totalRows++;

                if (isMissingPrimaryKey(payload.primaryKeyValue())) {
                    LOGGER.trace("Row {} missing primary key; marking as skipped", rowIndex + 1);
                    skipped++;
                    continue;
                }

                if (payload.columnValues().isEmpty()) {
                    LOGGER.trace("Row {} contains no mappable columns; marking as skipped", rowIndex + 1);
                    skipped++;
                    continue;
                }

                if (!existsByPrimaryKey(targetTable, primaryKeyColumn, payload.primaryKeyValue())) {
                    insertRow(targetTable, payload);
                    inserted++;
                } else {
                    boolean updatedRow = updateRow(targetTable, primaryKeyColumn, payload);
                    if (updatedRow) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            response.setTotalRows(totalRows);
            response.setInserted(inserted);
            response.setUpdated(updated);
            response.setSkipped(skipped);

            LOGGER.info("Data injector upload complete. Total: {}, Inserted: {}, Updated: {}, Skipped: {}",
                totalRows, inserted, updated, skipped);

            return response;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded Excel file", ex);
        } catch (RuntimeException ex) {
            LOGGER.trace("Data injector upload failed", ex);
            throw ex;
        }
    }

    private boolean existsByPrimaryKey(String tableName, String primaryKeyColumn, Object primaryKeyValue) {
        String sql = "SELECT COUNT(1) FROM " + tableName + " WHERE " + primaryKeyColumn + " = ?";
        Object value = convertValueForSql(primaryKeyValue);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count != null && count > 0;
    }

    private void insertRow(String tableName, RowPayload payload) {
        Map<String, Object> values = payload.columnValues();
        List<String> columns = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String columnName = sqlColumnName(entry.getKey());
            columns.add(columnName);
            parameters.add(convertValueForSql(entry.getValue()));
        }

        if (columns.isEmpty()) {
            LOGGER.trace("Row for primary key {} has no columns to insert; skipping", payload.primaryKeyValue());
            return;
        }

        String placeholders = String.join(", ", columns.stream().map(column -> "?").toList());
        String joinedColumns = String.join(", ", columns);
        String sql = "INSERT INTO " + tableName + " (" + joinedColumns + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, parameters.toArray());
    }

    private boolean updateRow(String tableName, String primaryKeyColumn, RowPayload payload) {
        Map<String, Object> values = payload.columnValues();
        Object primaryKeyValue = findValue(values, primaryKeyColumn);
        if (primaryKeyValue == null) {
            primaryKeyValue = payload.primaryKeyValue();
        }

        List<String> assignments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (matches(entry.getKey(), primaryKeyColumn)) {
                continue;
            }
            String columnName = sqlColumnName(entry.getKey());
            assignments.add(columnName + " = ?");
            parameters.add(convertValueForSql(entry.getValue()));
        }

        if (assignments.isEmpty()) {
            LOGGER.trace("No columns to update for primary key {}; skipping update", payload.primaryKeyValue());
            return false;
        }

        parameters.add(convertValueForSql(primaryKeyValue));

        String setClause = String.join(", ", assignments);
        String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + primaryKeyColumn + " = ?";
        int affected = jdbcTemplate.update(sql, parameters.toArray());
        return affected > 0;
    }

    private RowPayload readRowPayload(Row row, Map<Integer, ColumnBinding> headers) {
        Map<String, Object> values = new LinkedHashMap<>();
        Object primaryKeyValue = null;
        boolean hasValues = false;

        for (Map.Entry<Integer, ColumnBinding> entry : headers.entrySet()) {
            int columnIndex = entry.getKey();
            ColumnBinding binding = entry.getValue();
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Object value = cell != null ? readCellValue(cell) : null;
            if (value == null) {
                continue;
            }

            Object resolvedValue = applyExtractor(binding, value);
            if (resolvedValue == null) {
                continue;
            }

            if (resolvedValue instanceof String stringValue && stringValue.isBlank()) {
                continue;
            }

            String target = binding.targetColumn();
            if (target == null) {
                continue;
            }

            hasValues = true;
            values.put(target, resolvedValue);

            if (matches(target, properties.getPrimaryKey())) {
                primaryKeyValue = resolvedValue;
            }
        }

        return new RowPayload(primaryKeyValue, values, hasValues);
    }

    private Object applyExtractor(ColumnBinding binding, Object value) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof String stringValue)) {
            return value;
        }

        if (binding.extractorPattern() == null) {
            return stringValue.trim();
        }

        var matcher = binding.extractorPattern().matcher(stringValue);
        if (matcher.find()) {
            String extracted = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            return extracted != null ? extracted.trim() : null;
        }

        LOGGER.warn("Extractor pattern for column '{}' did not match value '{}'", binding.targetColumn(), stringValue);
        return stringValue.trim();
    }

    private Object readCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> {
                String text = cell.getStringCellValue();
                yield text != null ? text.trim() : null;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.rint(numericValue)) {
                    yield (long) numericValue;
                }
                yield numericValue;
            }
            case FORMULA -> readFormulaCell(cell);
            case BLANK, _NONE, ERROR -> null;
        };
    }

    private Object readFormulaCell(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> {
                String text = cell.getRichStringCellValue().getString();
                yield text != null ? text.trim() : null;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.rint(numericValue)) {
                    yield (long) numericValue;
                }
                yield numericValue;
            }
            case BLANK, ERROR, FORMULA, _NONE -> null;
        };
    }

    private Object convertValueForSql(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        if (value instanceof LocalDate localDate) {
            return Date.valueOf(localDate);
        }
        if (value instanceof LocalTime localTime) {
            return Time.valueOf(localTime);
        }
        if (value instanceof Double doubleValue) {
            return BigDecimal.valueOf(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return BigDecimal.valueOf(floatValue.doubleValue());
        }
        if (value instanceof String stringValue) {
            return stringValue.trim();
        }
        return value;
    }

    private Object findValue(Map<String, Object> values, String columnName) {
        return values.entrySet().stream()
            .filter(entry -> matches(entry.getKey(), columnName))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private boolean matches(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private boolean isMissingPrimaryKey(Object primaryKeyValue) {
        if (primaryKeyValue == null) {
            return true;
        }
        if (primaryKeyValue instanceof String stringValue) {
            return stringValue.trim().isEmpty();
        }
        return false;
    }

    private String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String sqlTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException("excel.target-table must be configured");
        }
        if (!tableName.matches("[A-Za-z0-9_.]+")) {
            throw new IllegalArgumentException("Invalid table name configured for excel.target-table: " + tableName);
        }
        return tableName;
    }

    private String requirePrimaryKey() {
        String primaryKey = properties.getPrimaryKey();
        if (primaryKey == null || primaryKey.isBlank()) {
            throw new IllegalStateException("excel.primary-key must be configured");
        }
        return primaryKey;
    }

    private String sqlColumnName(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("Column name cannot be blank");
        }
        if (!columnName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid column name: " + columnName);
        }
        return columnName;
    }

    private record RowPayload(Object primaryKeyValue, Map<String, Object> columnValues, boolean hasValues) {

        private RowPayload {
            columnValues = columnValues != null ? new LinkedHashMap<>(columnValues) : new LinkedHashMap<>();
        }

        public Object primaryKeyValue() {
            return primaryKeyValue;
        }

        public Map<String, Object> columnValues() {
            return columnValues;
        }

        public boolean hasValues() {
            return hasValues;
        }
    }
}
