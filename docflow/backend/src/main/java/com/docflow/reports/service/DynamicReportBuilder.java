package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DynamicReportBuilder {

    private static final Set<String> ALLOWED_OPERATORS = Set.of("EQ", "LIKE", "LT", "GT", "GE", "LE", "RANGE", "BETWEEN");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final ReportMetadataService metadataService;
    private final ReportProperties properties;

    public DynamicReportBuilder(ReportMetadataService metadataService, ReportProperties properties) {
        this.metadataService = metadataService;
        this.properties = properties;
    }

    public BuiltReport build(DynamicReportRequest request) {
        return build(request, "ad-hoc");
    }

    public BuiltReport build(DynamicReportRequest request, String contextLabel) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request required");
        }
        RequestContext ctx = RequestContext.from(request, properties, metadataService);

        ParameterCollector params = new ParameterCollector();
        List<SelectColumn> selectColumns = buildSelectColumns(ctx, params);
        List<String> whereClauses = buildWhereClauses(ctx, params);
        String metadataPivot = buildMetadataPivot(ctx, params);

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(selectColumns.stream()
                .map(sc -> sc.expression() + " AS " + sc.label())
                .collect(Collectors.joining(", ")));

        sql.append(" FROM ").append(ctx.baseTable()).append(" ").append(ctx.baseAlias());
        if (!ctx.baseTable().equals(ctx.documentTable())) {
            sql.append(" JOIN ").append(ctx.documentTable()).append(" ").append(ctx.documentAlias())
                    .append(" ON ")
                    .append(ctx.baseAlias()).append(".").append(ctx.businessFkColumn())
                    .append(" = ").append(ctx.documentAlias()).append(".").append(ctx.documentBusinessKey());
        }

        if (StringUtils.hasText(metadataPivot)) {
            sql.append(" LEFT JOIN (").append(metadataPivot).append(") md ON md.")
                    .append(ctx.documentIdColumn()).append(" = ").append(ctx.documentAlias()).append(".").append(ctx.documentInternalPk());
        }

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        BuiltReport built = new BuiltReport(sql.toString(), params.asMap(), selectColumns,
                ctx.metadataKeysUsed(), ctx.metadataTable(), ctx.metadataKeyColumn(), contextLabel);
        System.out.println("Generated SQL: " + built.sql());
        return built;
    }

    private List<SelectColumn> buildSelectColumns(RequestContext ctx, ParameterCollector params) {
        List<SelectColumn> selectColumns = new ArrayList<>();
        int idx = 0;
        for (RequestedColumn requested : ctx.requestedColumns()) {
            String label = "c" + idx++;
            String display = requested.original();
            String expression = switch (requested.type()) {
                case BASE -> ctx.baseAlias() + "." + requested.column();
                case DOCUMENT -> ctx.documentAlias() + "." + requested.column();
                case METADATA -> "md." + ctx.metadataAlias(requested.column());
            };
            selectColumns.add(new SelectColumn(label, display, expression));
        }

        // ensure document number is always available for downstream usage
        if (selectColumns.stream().noneMatch(c -> c.displayName().equalsIgnoreCase(ctx.documentBusinessKey()))) {
            selectColumns.add(0, new SelectColumn("doc_number", ctx.documentBusinessKey(), ctx.documentAlias() + "." + ctx.documentBusinessKey()));
        }

        return selectColumns;
    }

    private List<String> buildWhereClauses(RequestContext ctx, ParameterCollector params) {
        List<String> clauses = new ArrayList<>();
        for (RequestedFilter filter : ctx.filters()) {
            String op = normalizeOperator(filter.operator());
            if (!ALLOWED_OPERATORS.contains(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed: " + filter.operator());
            }
            validateOperatorForType(op, filter.dataType());
            validateFilterValueShape(filter, op);

            switch (filter.type()) {
                case BASE -> clauses.add(columnPredicate(ctx.baseAlias() + "." + filter.column(), op, filter, params));
                case DOCUMENT -> clauses.add(columnPredicate(ctx.documentAlias() + "." + filter.column(), op, filter, params));
                case METADATA -> clauses.add(buildMetadataExists(ctx, filter, op, params));
            }
        }
        return clauses;
    }

    private String buildMetadataPivot(RequestContext ctx, ParameterCollector params) {
        if (ctx.selectedMetadataKeys().isEmpty()) {
            return "";
        }
        String valueExpr = metadataValueExpression("dm", ctx.clobLimit(), ctx.valueIsClob(), false, false);
        List<String> projections = new ArrayList<>();
        int idx = 0;
        for (String key : ctx.selectedMetadataKeys()) {
            String alias = ctx.metadataAlias(key);
            String param = params.add(key);
            projections.add("MAX(CASE WHEN dm." + ctx.metadataKeyColumn() + " = :" + param + " THEN " + valueExpr + " END) AS " + alias);
            idx++;
        }
        String keyListParam = params.add(ctx.selectedMetadataKeys());
        return "SELECT dm." + ctx.documentIdColumn() + ", " + String.join(", ", projections)
                + " FROM " + ctx.metadataTable() + " dm"
                + " WHERE dm." + ctx.metadataKeyColumn() + " IN (:" + keyListParam + ")"
                + " GROUP BY dm." + ctx.documentIdColumn();
    }

    private String buildMetadataExists(RequestContext ctx, RequestedFilter filter, String op, ParameterCollector params) {
        StringBuilder exists = new StringBuilder("EXISTS (SELECT 1 FROM ")
                .append(ctx.metadataTable()).append(" dm WHERE dm.")
                .append(ctx.documentIdColumn()).append(" = ")
                .append(ctx.documentAlias()).append(".").append(ctx.documentInternalPk())
                .append(" AND dm.").append(ctx.metadataKeyColumn()).append(" = :").append(params.add(filter.column()));

        boolean numeric = "NUMBER".equalsIgnoreCase(filter.dataType());
        boolean date = "DATE".equalsIgnoreCase(filter.dataType());
        String valueExpr = metadataValueExpression("dm", ctx.clobLimit(), ctx.valueIsClob(), numeric, date);
        if (numeric || date) {
            exists.append(" AND ").append(valueExpr).append(" ")
                    .append(toSqlOperator(op, filter.value(), filter.valueFrom(), filter.valueTo(), filter.dataType(), params));
        } else {
            exists.append(" AND ").append(buildMetadataStringPredicate(valueExpr, op, filter.value(), params));
        }
        exists.append(")");
        return exists.toString();
    }

    private String buildMetadataStringPredicate(String valueExpr, String op, String value, ParameterCollector params) {
        String loweredExpr = "LOWER(" + valueExpr + ")";
        String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if ("LIKE".equals(op)) {
            String containsPattern = "%" + normalizedValue + "%";
            String quotedContainsPattern = "%\"" + normalizedValue + "\"%";
            String p1 = params.add(containsPattern);
            String p2 = params.add(quotedContainsPattern);
            return "(" + loweredExpr + " LIKE :" + p1 + " OR " + loweredExpr + " LIKE :" + p2 + ")";
        }
        String p1 = params.add(normalizedValue);
        String p2 = params.add("\"" + normalizedValue + "\"");
        String opSql = toSqlComparisonOperator(op);
        return "(" + loweredExpr + " " + opSql + " :" + p1 + " OR " + loweredExpr + " " + opSql + " :" + p2 + ")";
    }

    private String columnPredicate(String columnExpression, String op, RequestedFilter filter, ParameterCollector params) {
        String dataType = filter.dataType();
        String left = columnExpression;
        if ("NUMBER".equalsIgnoreCase(dataType)) {
            left = "TO_NUMBER(" + columnExpression + ")";
        } else if ("DATE".equalsIgnoreCase(dataType)) {
            // Treat Oracle DATE and TIMESTAMP columns the same for current "date-only" filters.
            left = "TRUNC(" + columnExpression + ")";
        } else if ("LIKE".equals(op)) {
            left = "LOWER(" + columnExpression + ")";
        }
        return left + " " + toSqlOperator(op, filter.value(), filter.valueFrom(), filter.valueTo(), dataType, params);
    }

    private String toSqlOperator(String op, String value, String valueFrom, String valueTo, String dataType, ParameterCollector params) {
        if ("LIKE".equals(op)) {
            String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
            return "LIKE :" + params.add("%" + normalized + "%");
        }
        if ("RANGE".equals(op) || "BETWEEN".equals(op)) {
            String fromParam = params.add(valueFrom);
            String toParam = params.add(valueTo);
            return "BETWEEN " + sqlParameterExpression(fromParam, dataType) + " AND " + sqlParameterExpression(toParam, dataType);
        }
        String paramName = params.add(value);
        return toSqlComparisonOperator(op) + " " + sqlParameterExpression(paramName, dataType);
    }

    private String sqlParameterExpression(String paramName, String dataType) {
        if ("NUMBER".equalsIgnoreCase(dataType)) {
            return "TO_NUMBER(:" + paramName + ")";
        }
        if ("DATE".equalsIgnoreCase(dataType)) {
            return "TO_DATE(:" + paramName + ", 'YYYY-MM-DD')";
        }
        return ":" + paramName;
    }

    private String normalizeOperator(String value) {
        if (value == null) {
            return "";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "=" -> "EQ";
            case "<" -> "LT";
            case ">" -> "GT";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String toSqlComparisonOperator(String opCode) {
        return switch (opCode) {
            case "EQ" -> "=";
            case "LT" -> "<";
            case "GT" -> ">";
            case "GE" -> ">=";
            case "LE" -> "<=";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed: " + opCode);
        };
    }

    private void validateOperatorForType(String opCode, String dataType) {
        if ("STRING".equalsIgnoreCase(dataType) && !"EQ".equals(opCode) && !"LIKE".equals(opCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for STRING type: " + opCode);
        }
        if ("NUMBER".equalsIgnoreCase(dataType)
                && !"EQ".equals(opCode)
                && !"LT".equals(opCode)
                && !"GT".equals(opCode)
                && !"RANGE".equals(opCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for NUMBER type: " + opCode);
        }
        if ("DATE".equalsIgnoreCase(dataType)
                && !"EQ".equals(opCode)
                && !"LT".equals(opCode)
                && !"GT".equals(opCode)
                && !"BETWEEN".equals(opCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for DATE type: " + opCode);
        }
        if (("NUMBER".equalsIgnoreCase(dataType) || "DATE".equalsIgnoreCase(dataType)) && "LIKE".equals(opCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for " + dataType + " type: " + opCode);
        }
    }

    private void validateFilterValueShape(RequestedFilter filter, String opCode) {
        if ("RANGE".equals(opCode) || "BETWEEN".equals(opCode)) {
            if (!StringUtils.hasText(filter.valueFrom()) || !StringUtils.hasText(filter.valueTo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Both range values are required for operator " + opCode);
            }
            validateRangeOrdering(filter, opCode);
            return;
        }
        if (!StringUtils.hasText(filter.value())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Value is required for operator " + opCode);
        }
    }

    private void validateRangeOrdering(RequestedFilter filter, String opCode) {
        if ("NUMBER".equalsIgnoreCase(filter.dataType())) {
            try {
                BigDecimal from = new BigDecimal(filter.valueFrom());
                BigDecimal to = new BigDecimal(filter.valueTo());
                if (from.compareTo(to) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid numeric " + opCode.toLowerCase(Locale.ROOT) + ": start must be <= end");
                }
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number format for operator " + opCode);
            }
        }
        if ("DATE".equalsIgnoreCase(filter.dataType())) {
            try {
                LocalDate from = LocalDate.parse(filter.valueFrom(), DATE_FORMATTER);
                LocalDate to = LocalDate.parse(filter.valueTo(), DATE_FORMATTER);
                if (from.isAfter(to)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid date " + opCode.toLowerCase(Locale.ROOT) + ": start must be <= end");
                }
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format for operator " + opCode);
            }
        }
    }

    private String metadataValueExpression(String alias, int clobLimit, boolean valueIsClob, boolean numeric, boolean date) {
        String base = valueIsClob
                ? "DBMS_LOB.SUBSTR(" + alias + "." + properties.getMetadataTable().getValueColumn() + ", " + clobLimit + ", 1)"
                : alias + "." + properties.getMetadataTable().getValueColumn();
        if (numeric) {
            return "TO_NUMBER(" + base + ")";
        }
        if (date) {
            return "TO_DATE(" + base + ", 'YYYY-MM-DD')";
        }
        return base;
    }

    public record BuiltReport(String sql, Map<String, Object> parameters, List<SelectColumn> columns,
                              List<String> metadataKeys, String metadataTable, String metadataKeyColumn,
                              String contextLabel) {
    }

    public record SelectColumn(String label, String displayName, String expression) {
    }

    private static class RequestContext {
        private final String baseTable;
        private final String baseAlias;
        private final String documentTable;
        private final String documentAlias;
        private final String documentInternalPk;
        private final String documentBusinessKey;
        private final String businessFkColumn;
        private final String metadataTable;
        private final String metadataKeyColumn;
        private final String metadataValueColumn;
        private final String documentIdColumn;
        private final boolean valueIsClob;
        private final int clobLimit;
        private final List<RequestedColumn> requestedColumns;
        private final List<RequestedFilter> filters;
        private final Map<String, String> metadataAliases = new LinkedHashMap<>();

        private RequestContext(String baseTable, String documentTable, String documentInternalPk, String documentBusinessKey,
                               String businessFkColumn, String metadataTable, String metadataKeyColumn, String metadataValueColumn,
                               String documentIdColumn, boolean valueIsClob, int clobLimit,
                               List<RequestedColumn> requestedColumns, List<RequestedFilter> filters) {
            this.baseTable = baseTable;
            this.documentTable = documentTable;
            this.baseAlias = baseTable.equalsIgnoreCase(documentTable) ? "dp" : "b";
            this.documentAlias = this.baseTable.equalsIgnoreCase(this.documentTable) ? this.baseAlias : "dp";
            this.documentInternalPk = documentInternalPk;
            this.documentBusinessKey = documentBusinessKey;
            this.businessFkColumn = businessFkColumn.toUpperCase(Locale.ROOT);
            this.metadataTable = metadataTable;
            this.metadataKeyColumn = metadataKeyColumn;
            this.metadataValueColumn = metadataValueColumn;
            this.documentIdColumn = documentIdColumn;
            this.valueIsClob = valueIsClob;
            this.clobLimit = clobLimit;
            this.requestedColumns = requestedColumns;
            this.filters = filters;
            int idx = 0;
            for (RequestedColumn col : requestedColumns) {
                if (col.type() == ColumnType.METADATA) {
                    metadataAliases.put(col.column(), "m" + idx++);
                }
            }
        }

        static RequestContext from(DynamicReportRequest request, ReportProperties properties, ReportMetadataService metadataService) {
            String base = requireEntity(request.getBaseEntity());
            validateBaseEntity(base, properties, metadataService);

            ReportProperties.DocumentTableProperties dp = properties.getDocumentTable();
            ReportProperties.MetadataTableProperties meta = properties.getMetadataTable();
            String businessFk = resolveBusinessFk(base, properties, metadataService);

            Set<String> baseColumns = new LinkedHashSet<>(metadataService.getColumns(base).columns());
            Set<String> documentColumns = new LinkedHashSet<>(metadataService.getColumns(dp.getName()).columns());
            List<String> metadataKeys = metadataService.listMetadataKeys();

            List<RequestedColumn> columns = parseColumns(request.getColumns(), base, dp.getName(), dp.getBusinessKey(), baseColumns, documentColumns, metadataKeys);
            List<RequestedFilter> filters = parseFilters(request.getFilters(), base, dp.getName(), baseColumns, documentColumns, metadataKeys);

            return new RequestContext(
                    base,
                    dp.getName().toUpperCase(Locale.ROOT),
                    dp.getInternalPk().toUpperCase(Locale.ROOT),
                    dp.getBusinessKey().toUpperCase(Locale.ROOT),
                    businessFk,
                    meta.getName().toUpperCase(Locale.ROOT),
                    meta.getKeyColumn().toUpperCase(Locale.ROOT),
                    meta.getValueColumn().toUpperCase(Locale.ROOT),
                    meta.getDocumentIdColumn().toUpperCase(Locale.ROOT),
                    meta.isValueIsClob(),
                    meta.getClobSelectLimit(),
                    columns,
                    filters
            );
        }

        private static String resolveBusinessFk(String base, ReportProperties properties, ReportMetadataService metadataService) {
            if (base.equalsIgnoreCase(properties.getDocumentTable().getName())) {
                return properties.getDocumentTable().getBusinessKey();
            }
            for (ReportProperties.EntityProperties entity : properties.getEnabledEntities()) {
                if (base.equalsIgnoreCase(entity.getName())) {
                    if (entity.getJoinToDocument() == null || !entity.getJoinToDocument().isEnabled()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join to document is required for base entity");
                    }
                    String fk = entity.getJoinToDocument().getBusinessFkColumn();
                    if (!metadataService.getColumns(base).columns().contains(fk.toUpperCase(Locale.ROOT))) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join column not found on base entity: " + fk);
                    }
                    return fk.toUpperCase(Locale.ROOT);
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entity not permitted: " + base);
        }

        private static void validateBaseEntity(String base, ReportProperties properties, ReportMetadataService metadataService) {
            if (base.equalsIgnoreCase(properties.getDocumentTable().getName())) {
                return;
            }
            boolean allowed = properties.getEnabledEntities().stream()
                    .anyMatch(e -> base.equalsIgnoreCase(e.getName()));
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entity not permitted: " + base);
            }
            metadataService.getColumns(base); // ensure exists
        }

        private static List<RequestedColumn> parseColumns(List<String> rawColumns,
                                                          String base,
                                                          String documentTable,
                                                          String documentBusinessKey,
                                                          Set<String> baseColumns,
                                                          Set<String> documentColumns,
                                                          List<String> metadataKeys) {
            List<RequestedColumn> parsed = new ArrayList<>();
            if (rawColumns != null) {
                for (String raw : rawColumns) {
                    if (!StringUtils.hasText(raw)) {
                        continue;
                    }
                    parsed.add(parseColumn(raw.trim(), base, documentTable, baseColumns, documentColumns, metadataKeys));
                }
            }
            if (parsed.isEmpty()) {
                parsed.add(new RequestedColumn(ColumnType.DOCUMENT, documentTable, documentBusinessKey.toUpperCase(Locale.ROOT), documentBusinessKey));
            }
            return parsed;
        }

        private static RequestedColumn parseColumn(String value,
                                                   String base,
                                                   String documentTable,
                                                   Set<String> baseColumns,
                                                   Set<String> documentColumns,
                                                   List<String> metadataKeys) {
            if (value.toLowerCase(Locale.ROOT).startsWith("meta:")) {
                String key = value.substring(5);
                if (metadataKeys.stream().noneMatch(k -> k.equalsIgnoreCase(key))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown metadata key: " + key);
                }
                return new RequestedColumn(ColumnType.METADATA, "META", key, value);
            }
            String entity = base;
            String column = value;
            if (value.contains(".")) {
                String[] parts = value.split("\\.", 2);
                entity = parts[0];
                column = parts[1];
            }
            ColumnType type;
            if (entity.equalsIgnoreCase(base)) {
                type = ColumnType.BASE;
                ensureColumn(baseColumns, column, base);
            } else if (entity.equalsIgnoreCase(documentTable) || entity.equalsIgnoreCase("DOCUMENT") || entity.equalsIgnoreCase("DOCUMENT_PARENT")) {
                type = ColumnType.DOCUMENT;
                ensureColumn(documentColumns, column, "document");
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid column entity: " + entity);
            }
            return new RequestedColumn(type, entity, column.toUpperCase(Locale.ROOT), value);
        }

        private static List<RequestedFilter> parseFilters(List<ReportFilter> filters,
                                                          String base,
                                                          String documentTable,
                                                          Set<String> baseColumns,
                                                          Set<String> documentColumns,
                                                          List<String> metadataKeys) {
            List<RequestedFilter> parsed = new ArrayList<>();
            if (filters == null) {
                return parsed;
            }
            for (ReportFilter filter : filters) {
                if (filter == null || !StringUtils.hasText(filter.getKey())) {
                    continue;
                }
                if (!hasAnyFilterValue(filter)) {
                    continue;
                }
                parsed.add(parseFilter(filter, base, documentTable, baseColumns, documentColumns, metadataKeys));
            }
            return parsed;
        }

        private static boolean hasAnyFilterValue(ReportFilter filter) {
            return StringUtils.hasText(filter.getValue())
                    || StringUtils.hasText(filter.getValueFrom())
                    || StringUtils.hasText(filter.getValueTo());
        }

        private static RequestedFilter parseFilter(ReportFilter filter,
                                                   String base,
                                                   String documentTable,
                                                   Set<String> baseColumns,
                                                   Set<String> documentColumns,
                                                   List<String> metadataKeys) {
            String key = filter.getKey();
            String op = filter.getOp();
            String value = filter.getValue();
            if (!StringUtils.hasText(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter operator required");
            }
            if (key.toLowerCase(Locale.ROOT).startsWith("meta:")) {
                String metaKey = key.substring(5);
                if (metadataKeys.stream().noneMatch(k -> k.equalsIgnoreCase(metaKey))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown metadata key: " + metaKey);
                }
                return new RequestedFilter(ColumnType.METADATA, metaKey, op, value,
                        optionalTrim(filter.getValueFrom()), optionalTrim(filter.getValueTo()), resolveFilterType(filter));
            }

            String entity = base;
            String column = key;
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                entity = parts[0];
                column = parts[1];
            }
            if (entity.equalsIgnoreCase(base)) {
                ensureColumn(baseColumns, column, base);
                return new RequestedFilter(ColumnType.BASE, column.toUpperCase(Locale.ROOT), op, value,
                        optionalTrim(filter.getValueFrom()), optionalTrim(filter.getValueTo()), resolveFilterType(filter));
            }
            if (entity.equalsIgnoreCase(documentTable) || entity.equalsIgnoreCase("DOCUMENT") || entity.equalsIgnoreCase("DOCUMENT_PARENT")) {
                ensureColumn(documentColumns, column, "document");
                return new RequestedFilter(ColumnType.DOCUMENT, column.toUpperCase(Locale.ROOT), op, value,
                        optionalTrim(filter.getValueFrom()), optionalTrim(filter.getValueTo()), resolveFilterType(filter));
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filter entity: " + entity);
        }

        private static String optionalTrim(String value) {
            return value == null ? null : value.trim();
        }

        private static String resolveFilterType(ReportFilter filter) {
            if (filter.getLogicalType() != null) {
                return filter.getLogicalType().name();
            }
            return filter.getDataType();
        }

        private static String requireEntity(String value) {
            if (!StringUtils.hasText(value)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseEntity is required");
            }
            String trimmed = value.trim().toUpperCase(Locale.ROOT);
            if (!trimmed.matches("[A-Z0-9_]+")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entity name: " + value);
            }
            return trimmed;
        }

        private static void ensureColumn(Set<String> available, String column, String entity) {
            String normalized = column.toUpperCase(Locale.ROOT);
            if (!available.contains(normalized)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown column " + column + " on " + entity);
            }
        }

        String baseTable() {
            return baseTable;
        }

        String baseAlias() {
            return baseAlias;
        }

        String documentTable() {
            return documentTable;
        }

        String documentAlias() {
            return documentAlias;
        }

        String documentInternalPk() {
            return documentInternalPk;
        }

        String documentBusinessKey() {
            return documentBusinessKey;
        }

        String businessFkColumn() {
            return businessFkColumn;
        }

        String metadataTable() {
            return metadataTable;
        }

        String metadataKeyColumn() {
            return metadataKeyColumn;
        }

        String metadataValueColumn() {
            return metadataValueColumn;
        }

        String documentIdColumn() {
            return documentIdColumn;
        }

        boolean valueIsClob() {
            return valueIsClob;
        }

        int clobLimit() {
            return clobLimit;
        }

        List<RequestedColumn> requestedColumns() {
            return requestedColumns;
        }

        List<RequestedFilter> filters() {
            return filters;
        }

        List<String> selectedMetadataKeys() {
            return requestedColumns.stream()
                    .filter(c -> c.type() == ColumnType.METADATA)
                    .map(RequestedColumn::column)
                    .distinct()
                    .toList();
        }

        List<String> metadataKeysUsed() {
            Set<String> keys = new LinkedHashSet<>(selectedMetadataKeys());
            filters.stream()
                    .filter(f -> f.type() == ColumnType.METADATA)
                    .map(RequestedFilter::column)
                    .forEach(keys::add);
            return new ArrayList<>(keys);
        }

        String metadataAlias(String key) {
            String alias = metadataAliases.get(key);
            if (alias == null) {
                alias = "m" + metadataAliases.size();
                metadataAliases.put(key, alias);
            }
            return alias;
        }
    }

    private record RequestedColumn(ColumnType type, String entity, String column, String original) {
    }

    private record RequestedFilter(ColumnType type, String column, String operator, String value,
                                   String valueFrom, String valueTo, String dataType) {
    }

    private enum ColumnType {
        BASE,
        DOCUMENT,
        METADATA
    }

    private static class ParameterCollector {
        private final Map<String, Object> values = new LinkedHashMap<>();

        String add(Object value) {
            String name = "p" + values.size();
            values.put(name, value);
            return name;
        }

        Map<String, Object> asMap() {
            return values;
        }
    }
}
