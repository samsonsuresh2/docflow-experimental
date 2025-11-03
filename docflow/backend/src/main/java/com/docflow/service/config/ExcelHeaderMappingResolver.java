package com.docflow.service.config;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ExcelHeaderMappingResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelHeaderMappingResolver.class);

    private final DataInjectorProperties properties;

    public ExcelHeaderMappingResolver(DataInjectorProperties properties) {
        this.properties = properties;
    }

    public HeaderDescriptor resolve(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Uploaded Excel file does not contain a header row");
        }

        Map<Integer, ColumnBinding> resolved = new LinkedHashMap<>();
        Set<String> ignored = new LinkedHashSet<>();

        short lastCellNum = headerRow.getLastCellNum();
        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            Cell cell = headerRow.getCell(columnIndex);
            String headerText = cell != null ? Objects.toString(readHeaderValue(cell), null) : null;
            if (headerText == null || headerText.isBlank()) {
                ignored.add("Column" + (columnIndex + 1));
                continue;
            }

            String trimmedHeader = headerText.trim();
            Optional<String> explicit = properties.findMapping(trimmedHeader);
            String target = explicit.orElseGet(() -> resolveDirect(trimmedHeader).orElse(null));

            if (target == null) {
                LOGGER.info("Ignoring unmapped header '{}'", trimmedHeader);
                ignored.add(trimmedHeader);
                continue;
            }

            Pattern extractor = properties.findExtractor(target)
                    .filter(regex -> !regex.isBlank())
                    .map(this::compilePattern)
                    .orElse(null);

            resolved.put(columnIndex, new ColumnBinding(trimmedHeader, target, extractor));
        }

        return new HeaderDescriptor(resolved, ignored);
    }

    private Optional<String> resolveDirect(String header) {
        Set<String> knownColumns = new LinkedHashSet<>(properties.knownColumns());
        if (properties.getPrimaryKey() != null) {
            knownColumns.add(properties.getPrimaryKey());
        }
        String normalizedHeader = normalize(header);
        return knownColumns.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> normalize(candidate).equals(normalizedHeader))
                .findFirst();
    }

    private Object readHeaderValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> {
                String text = cell.getStringCellValue();
                yield text != null ? text.trim() : null;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> cell.getNumericCellValue();
            case FORMULA -> {
                String text = cell.getStringCellValue();
                yield text != null ? text.trim() : null;
            }
            case BLANK, _NONE, ERROR -> null;
        };
    }

    private Pattern compilePattern(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (Exception ex) {
            LOGGER.warn("Invalid extractor pattern '{}': {}", regex, ex.getMessage());
            return null;
        }
    }

    private String normalize(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    public record HeaderDescriptor(Map<Integer, ColumnBinding> mappedColumns, Set<String> ignoredHeaders) {

        public Map<Integer, ColumnBinding> mappedColumns() {
            return mappedColumns;
        }

        public Set<String> ignoredHeaders() {
            return ignoredHeaders;
        }

        public boolean hasColumn(String columnName) {
            if (columnName == null) {
                return false;
            }
            String normalized = columnName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            return mappedColumns.values().stream()
                    .map(ColumnBinding::targetColumn)
                    .filter(Objects::nonNull)
                    .map(value -> value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT))
                    .anyMatch(candidate -> candidate.equals(normalized));
        }
    }

    public record ColumnBinding(String originalHeader, String targetColumn, Pattern extractorPattern) {
    }
}
