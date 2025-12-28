package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportTemplateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReportExecutionService {

    private static final List<String> DEFAULT_NUMERIC_OPS = List.of("=", "<", ">");
    private static final List<String> DEFAULT_TEXT_OPS = List.of("=");
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final ReportTemplateService templateService;
    private final DynamicReportBuilder builder;
    private final DynamicReportExecutor executor;

    public ReportExecutionService(ReportTemplateService templateService,
                                  DynamicReportBuilder builder,
                                  DynamicReportExecutor executor) {
        this.templateService = templateService;
        this.builder = builder;
        this.executor = executor;
    }

    public List<ReportExecutionModels.TemplateSummary> listExecutableTemplates() {
        return templateService.listTemplates().stream()
                .map(t -> new ReportExecutionModels.TemplateSummary(t.getId(), t.getName(), t.getDescription()))
                .toList();
    }

    public ReportExecutionModels.TemplateDetail getExecutableTemplate(long templateId) {
        TemplateContext ctx = TemplateContext.from(templateService.getById(templateId));
        return ctx.toDetail();
    }

    public ReportExecutionModels.RunResponse run(ReportExecutionModels.RunRequest request, int page, int size) {
        if (request == null || request.getTemplateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "templateId is required");
        }
        TemplateContext ctx = TemplateContext.from(templateService.getById(request.getTemplateId()));
        List<ReportFilter> filters = new ArrayList<>(ctx.fixedFilters());

        Map<String, TemplateFilterDefinition> allowedFilters = ctx.userFiltersByLookup();
        Set<String> seenKeys = new HashSet<>();
        for (ReportExecutionModels.RunFilter input : request.getFilters()) {
            if (input == null) {
                continue;
            }
            String lookupKey = normalizeKey(input.getKey());
            if (!StringUtils.hasText(lookupKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter key is required");
            }
            TemplateFilterDefinition definition = allowedFilters.get(lookupKey);
            if (definition == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown filter key: " + input.getKey());
            }
            String op = normalizeOp(input.getOp());
            if (!definition.allowedOps().contains(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for " + definition.label() + ": " + op);
            }
            String value = input.getValue();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!seenKeys.add(lookupKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate filter provided: " + input.getKey());
            }
            String cleanedValue = sanitizeValue(definition.type(), value.trim(), definition.dateFormat());

            ReportFilter filter = new ReportFilter();
            filter.setKey(definition.originalKey());
            filter.setOp(op);
            filter.setValue(cleanedValue);
            filter.setDataType(definition.dataType());
            filters.add(filter);
        }

        DynamicReportRequest dynamicRequest = new DynamicReportRequest();
        dynamicRequest.setBaseEntity(ctx.baseEntity());
        dynamicRequest.setColumns(ctx.columns());
        dynamicRequest.setFilters(filters);

        DynamicReportBuilder.BuiltReport built = builder.build(dynamicRequest);
        Map<String, Object> raw = executor.execute(built, page, size);

        List<String> columns = safeList(raw.get("columns"));
        List<Map<String, Object>> rows = safeRowList(raw.get("rows"));
        return new ReportExecutionModels.RunResponse(columns, rows, rows.size());
    }

    private String sanitizeValue(ReportExecutionModels.FieldType type, String value, String dateFormat) {
        return switch (type) {
            case NUMBER -> validateNumber(value);
            case DATE -> validateDate(value, dateFormat);
            case TEXT -> validateText(value);
        };
    }

    private String validateNumber(String value) {
        try {
            new BigDecimal(value);
            return value;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number format: " + value);
        }
    }

    private String validateDate(String value, String dateFormat) {
        String pattern = StringUtils.hasText(dateFormat) ? dateFormat : DEFAULT_DATE_FORMAT;
        DateTimeFormatter formatter = DEFAULT_DATE_FORMAT.equals(pattern)
                ? DATE_FORMATTER
                : DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
        try {
            LocalDate parsed = LocalDate.parse(value, formatter);
            return parsed.format(formatter);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format, expected " + pattern);
        }
    }

    private String validateText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is required");
        }
        return value.trim();
    }

    private static String normalizeOp(String op) {
        if (!StringUtils.hasText(op)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter operator required");
        }
        String normalized = op.trim();
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private List<String> safeList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeRowList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> safe = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() != null) {
                            row.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                    safe.add(row);
                }
            }
            return safe;
        }
        return List.of();
    }

    private static class TemplateContext {
        private final long templateId;
        private final String name;
        private final String baseEntity;
        private final List<String> columns;
        private final Map<String, TemplateFilterDefinition> userFilters;
        private final List<ReportFilter> fixedFilters;

        private TemplateContext(long templateId,
                                String name,
                                String baseEntity,
                                List<String> columns,
                                Map<String, TemplateFilterDefinition> userFilters,
                                List<ReportFilter> fixedFilters) {
            this.templateId = templateId;
            this.name = name;
            this.baseEntity = baseEntity;
            this.columns = columns;
            this.userFilters = userFilters;
            this.fixedFilters = fixedFilters;
        }

        static TemplateContext from(ReportTemplateResponse template) {
            if (template == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
            }
            DynamicReportRequest request = template.getRequest();
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Template is missing report definition");
            }
            String baseEntity = requireNonBlank(request.getBaseEntity(), "Template base entity missing");
            List<String> columns = new ArrayList<>(
                Objects.requireNonNullElse(request.getColumns(), List.<String>of())
            );
            Map<String, TemplateFilterDefinition> userFilters = new LinkedHashMap<>();
            List<ReportFilter> fixedFilters = new ArrayList<>();
            for (ReportFilter filter : Objects.requireNonNullElse(request.getFilters(), List.<ReportFilter>of())) {
            if (filter == null || !StringUtils.hasText(filter.getKey())) {
                continue;
            }
            ReportFilter.Mode mode = filter.getMode();
            boolean treatAsUserInput = mode == null
                    || mode == ReportFilter.Mode.USER_INPUT
                    || (mode == ReportFilter.Mode.FIXED_VALUE && !StringUtils.hasText(filter.getValue()));
            TemplateFilterDefinition definition = toDefinition(filter);
            if (treatAsUserInput) {
                if (userFilters.containsKey(definition.lookupKey())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate filter key in template: " + definition.originalKey());
                }
                userFilters.put(definition.lookupKey(), definition);
            } else if (StringUtils.hasText(filter.getValue())) {
                String storedOp = normalizeFixedOp(filter.getOp());
                if (!definition.allowedOps().contains(storedOp)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fixed filter operator not allowed for " + definition.label());
                }
                ReportFilter fixed = new ReportFilter();
                fixed.setKey(definition.originalKey());
                fixed.setOp(storedOp);
                fixed.setValue(filter.getValue());
                fixed.setDataType(definition.dataType());
                fixedFilters.add(fixed);
            }
        }
            return new TemplateContext(template.getId(), template.getName(), baseEntity, columns, userFilters, fixedFilters);
        }

        TemplateFilterDefinition definitionFor(String key) {
            return userFilters.get(normalizeKey(key));
        }

        Map<String, TemplateFilterDefinition> userFiltersByLookup() {
            return userFilters;
        }

        List<ReportFilter> fixedFilters() {
            return fixedFilters;
        }

        String baseEntity() {
            return baseEntity;
        }

        List<String> columns() {
            return columns;
        }

        ReportExecutionModels.TemplateDetail toDetail() {
            List<ReportExecutionModels.FilterField> filters = userFilters.values().stream()
                    .map(def -> new ReportExecutionModels.FilterField(
                            def.originalKey(),
                            def.label(),
                            def.type(),
                            def.allowedOps(),
                            def.dateFormat()
                    ))
                    .toList();
            return new ReportExecutionModels.TemplateDetail(templateId, name, filters);
        }

        private static TemplateFilterDefinition toDefinition(ReportFilter filter) {
            String originalKey = filter.getKey().trim();
            String lookupKey = normalizeKey(filter.getKey());
            ReportExecutionModels.FieldType type = resolveType(filter.getDataType());
            String label = deriveLabel(filter.getLabel(), originalKey);
            List<String> allowedOps = defaultOps(type);
            String dateFormat = type == ReportExecutionModels.FieldType.DATE ? DEFAULT_DATE_FORMAT : null;
            return new TemplateFilterDefinition(originalKey, lookupKey, label, type, allowedOps, dateFormat, normalizeDataType(type));
        }

        private static String deriveLabel(String provided, String key) {
            if (StringUtils.hasText(provided)) {
                return provided.trim();
            }
            if (key.contains(".")) {
                String[] parts = key.split("\\.");
                return parts[parts.length - 1].replace('_', ' ').trim();
            }
            if (key.toLowerCase(Locale.ROOT).startsWith("meta:")) {
                return key.substring(5);
            }
            return key;
        }

        private static String normalizeFixedOp(String op) {
            if (!StringUtils.hasText(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fixed filter operator is required");
            }
            return op.trim().toLowerCase(Locale.ROOT);
        }

        private static List<String> defaultOps(ReportExecutionModels.FieldType type) {
            return switch (type) {
                case NUMBER, DATE -> DEFAULT_NUMERIC_OPS;
                case TEXT -> DEFAULT_TEXT_OPS;
            };
        }

        private static ReportExecutionModels.FieldType resolveType(String dataType) {
            if (!StringUtils.hasText(dataType)) {
                return ReportExecutionModels.FieldType.TEXT;
            }
            String normalized = dataType.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "NUMBER" -> ReportExecutionModels.FieldType.NUMBER;
                case "DATE" -> ReportExecutionModels.FieldType.DATE;
                default -> ReportExecutionModels.FieldType.TEXT;
            };
        }

        private static String normalizeDataType(ReportExecutionModels.FieldType type) {
            return switch (type) {
                case NUMBER -> "NUMBER";
                case DATE -> "DATE";
                case TEXT -> "TEXT";
            };
        }

        private static String requireNonBlank(String value, String message) {
            if (!StringUtils.hasText(value)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
            }
            return value.trim();
        }
    }

    private record TemplateFilterDefinition(
            String originalKey,
            String lookupKey,
            String label,
            ReportExecutionModels.FieldType type,
            List<String> allowedOps,
            String dateFormat,
            String dataType
    ) {
    }
}
