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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReportExecutionService {

    private static final List<String> STRING_OPS = List.of("EQ", "LIKE");
    private static final List<String> NUMBER_OPS = List.of("EQ", "LT", "GT", "RANGE");
    private static final List<String> DATE_OPS = List.of("EQ", "LT", "GT", "BETWEEN");
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private final ReportTemplateService templateService;
    private final DynamicReportBuilder builder;
    private final DynamicReportExecutor executor;
    private final DatePresetService datePresetService;
    private final ReportFilterTypeValidationService filterTypeValidationService;

    public ReportExecutionService(ReportTemplateService templateService,
                                  DynamicReportBuilder builder,
                                  DynamicReportExecutor executor,
                                  DatePresetService datePresetService,
                                  ReportFilterTypeValidationService filterTypeValidationService) {
        this.templateService = templateService;
        this.builder = builder;
        this.executor = executor;
        this.datePresetService = datePresetService;
        this.filterTypeValidationService = filterTypeValidationService;
    }

    public List<ReportExecutionModels.TemplateSummary> listExecutableTemplates() {
        return templateService.listTemplates().stream()
                .map(t -> new ReportExecutionModels.TemplateSummary(t.getId(), t.getName(), t.getDescription()))
                .toList();
    }

    public ReportExecutionModels.TemplateDetail getExecutableTemplate(long templateId) {
        TemplateContext ctx = TemplateContext.from(templateService.getById(templateId), datePresetService);
        return ctx.toDetail();
    }

    public ReportExecutionModels.RunResponse runAll(ReportExecutionModels.RunRequest request, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        ReportExecutionModels.RunResponse firstPage = run(request, 0, safePageSize);
        if (firstPage.rowCount() <= firstPage.rows().size()) {
            return firstPage;
        }

        List<Map<String, Object>> allRows = new ArrayList<>(firstPage.rows());
        int totalPages = (int) Math.max(1, Math.ceil((double) firstPage.rowCount() / safePageSize));
        for (int page = 1; page < totalPages; page++) {
            ReportExecutionModels.RunResponse nextPage = run(request, page, safePageSize);
            allRows.addAll(nextPage.rows());
        }
        return new ReportExecutionModels.RunResponse(firstPage.columns(), allRows, firstPage.rowCount());
    }

    public ReportExecutionModels.RunResponse run(ReportExecutionModels.RunRequest request, int page, int size) {
        if (request == null || request.getTemplateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "templateId is required");
        }
        TemplateContext ctx = TemplateContext.from(templateService.getById(request.getTemplateId()), datePresetService);
        filterTypeValidationService.validateRuntimeDefinition(ctx.template().getRequest(), "template:" + ctx.templateId());
        List<ReportFilter> filters = new ArrayList<>(ctx.fixedFilters());
        List<ReportFilter> runtimeFilters = new ArrayList<>();

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
            if (!seenKeys.add(lookupKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate filter provided: " + input.getKey());
            }

            if (definition.type() == ReportExecutionModels.FieldType.DATE && input.getMode() != null) {
                List<ReportFilter> normalizedDateFilters = normalizeDateModeFilter(input, definition, ctx.name());
                runtimeFilters.addAll(normalizedDateFilters);
                filters.addAll(normalizedDateFilters);
                continue;
            }

            String op = normalizeOpCode(input.getOp());
            if (!definition.allowedOps().contains(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for " + definition.label() + ": " + op);
            }
            if (requiresRangeValues(op)) {
                String valueFrom = normalizeOptional(input.getValueFrom());
                String valueTo = normalizeOptional(input.getValueTo());
                if (!StringUtils.hasText(valueFrom) && !StringUtils.hasText(valueTo)) {
                    continue;
                }
                if (!StringUtils.hasText(valueFrom) || !StringUtils.hasText(valueTo)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Both values are required for " + definition.label() + " " + op.toLowerCase(Locale.ROOT));
                }
                String cleanFrom = sanitizeValue(definition.type(), valueFrom, definition.dateFormat());
                String cleanTo = sanitizeValue(definition.type(), valueTo, definition.dateFormat());
                validateRange(definition, cleanFrom, cleanTo, op);

                ReportFilter filter = new ReportFilter();
                filter.setKey(definition.originalKey());
                filter.setOp(op);
                filter.setValueFrom(cleanFrom);
                filter.setValueTo(cleanTo);
                filter.setDataType(definition.dataType());
                runtimeFilters.add(filter);
                filters.add(filter);
                continue;
            }

            String value = normalizeOptional(input.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String cleanedValue = sanitizeValue(definition.type(), value, definition.dateFormat());

            ReportFilter filter = new ReportFilter();
            filter.setKey(definition.originalKey());
            filter.setOp(op);
            filter.setValue(cleanedValue);
            filter.setDataType(definition.dataType());
            runtimeFilters.add(filter);
            filters.add(filter);
        }

        ReportRunPolicy.validateAtLeastOneRuntimeFilter(runtimeFilters);

        DynamicReportRequest dynamicRequest = new DynamicReportRequest();
        dynamicRequest.setBaseEntity(ctx.baseEntity());
        dynamicRequest.setColumns(ctx.columns());
        dynamicRequest.setFilters(filters);

        DynamicReportBuilder.BuiltReport built = builder.build(dynamicRequest, "template:" + ctx.templateId());
        Map<String, Object> raw = executor.execute(built, page, size);

        List<String> columns = safeList(raw.get("columns"));
        List<Map<String, Object>> rows = safeRowList(raw.get("rows"));
        long rowCount = safeLong(raw.get("rowCount"), rows.size());
        return new ReportExecutionModels.RunResponse(columns, rows, rowCount);
    }

    private List<ReportFilter> normalizeDateModeFilter(ReportExecutionModels.RunFilter input,
                                                       TemplateFilterDefinition definition,
                                                       String reportCode) {
        if (definition.type() != ReportExecutionModels.FieldType.DATE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preset mode allowed only for DATE filters");
        }

        ReportExecutionModels.DateFilterMode mode = input.getMode();
        if (mode == ReportExecutionModels.DateFilterMode.PRESET) {
            DatePresetService.ResolvedDateRange range = datePresetService.resolvePreset(reportCode, definition.originalKey(), input.getPresetCode());
            return rangeFilters(definition, range.fromDate(), range.toDate());
        }
        if (mode == ReportExecutionModels.DateFilterMode.MANUAL) {
            String op = normalizeOpCode(input.getOp());
            if (!StringUtils.hasText(op)) {
                op = definition.allowedOps().contains("BETWEEN") ? "BETWEEN" : "EQ";
            }
            if (!definition.allowedOps().contains(op)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator not allowed for " + definition.label() + ": " + op);
            }

            String legacyFrom = normalizeOptional(input.getFromValue());
            String legacyTo = normalizeOptional(input.getToValue());
            String valueFrom = normalizeOptional(input.getValueFrom());
            String valueTo = normalizeOptional(input.getValueTo());
            String singleValue = normalizeOptional(input.getValue());

            if ("BETWEEN".equals(op)) {
                String fromCandidate = StringUtils.hasText(valueFrom) ? valueFrom : legacyFrom;
                String toCandidate = StringUtils.hasText(valueTo) ? valueTo : legacyTo;
                if (!StringUtils.hasText(fromCandidate) && !StringUtils.hasText(toCandidate)) {
                    return List.of();
                }
                if (!StringUtils.hasText(fromCandidate) || !StringUtils.hasText(toCandidate)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Both valueFrom and valueTo are required for " + definition.label());
                }
                LocalDate from = LocalDate.parse(validateDate(fromCandidate, definition.dateFormat()), DATE_FORMATTER);
                LocalDate to = LocalDate.parse(validateDate(toCandidate, definition.dateFormat()), DATE_FORMATTER);
                if (from.isAfter(to)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid manual date range for " + definition.label());
                }
                ReportFilter between = new ReportFilter();
                between.setKey(definition.originalKey());
                between.setOp("BETWEEN");
                between.setValueFrom(from.format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));
                between.setValueTo(to.format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));
                between.setDataType(definition.dataType());
                return List.of(between);
            }

            if (!StringUtils.hasText(singleValue)) {
                return List.of();
            }
            String cleanedValue = validateDate(singleValue, definition.dateFormat());
            ReportFilter manual = new ReportFilter();
            manual.setKey(definition.originalKey());
            manual.setOp(op);
            manual.setValue(cleanedValue);
            manual.setDataType(definition.dataType());
            return List.of(manual);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown date filter mode for " + definition.label());
    }

    private List<ReportFilter> rangeFilters(TemplateFilterDefinition definition, LocalDate from, LocalDate to) {
        ReportFilter fromFilter = new ReportFilter();
        fromFilter.setKey(definition.originalKey());
        fromFilter.setOp("GE");
        fromFilter.setValue(from.format(DATE_FORMATTER));
        fromFilter.setDataType(definition.dataType());

        ReportFilter toFilter = new ReportFilter();
        toFilter.setKey(definition.originalKey());
        toFilter.setOp("LE");
        toFilter.setValue(to.format(DATE_FORMATTER));
        toFilter.setDataType(definition.dataType());

        return List.of(fromFilter, toFilter);
    }

    private String sanitizeValue(ReportExecutionModels.FieldType type, String value, String dateFormat) {
        return switch (type) {
            case NUMBER -> validateNumber(value);
            case DATE -> validateDate(value, dateFormat);
            case STRING -> validateText(value);
        };
    }

    private void validateRange(TemplateFilterDefinition definition, String fromValue, String toValue, String op) {
        if (definition.type() == ReportExecutionModels.FieldType.NUMBER) {
            BigDecimal from = new BigDecimal(fromValue);
            BigDecimal to = new BigDecimal(toValue);
            if (from.compareTo(to) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid range for " + definition.label());
            }
            return;
        }
        if (definition.type() == ReportExecutionModels.FieldType.DATE) {
            LocalDate from = LocalDate.parse(fromValue, DATE_FORMATTER);
            LocalDate to = LocalDate.parse(toValue, DATE_FORMATTER);
            if (from.isAfter(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + op.toLowerCase(Locale.ROOT) + " for " + definition.label());
            }
        }
    }

    private String validateNumber(String value) {
        if (!value.matches("^-?\\d+(\\.\\d+)?$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number format: " + value);
        }
        try {
            new BigDecimal(value);
            return value;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number format: " + value);
        }
    }

    private String validateDate(String value, String dateFormat) {
        String pattern = StringUtils.hasText(dateFormat) ? dateFormat : DEFAULT_DATE_FORMAT;
        try {
            LocalDate parsed;
            if (DEFAULT_DATE_FORMAT.equals(pattern)) {
                parsed = LocalDate.parse(value, DATE_FORMATTER);
                return parsed.format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
            parsed = LocalDate.parse(value, formatter);
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

    private static boolean requiresRangeValues(String op) {
        return "RANGE".equals(op) || "BETWEEN".equals(op);
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOpCode(String op) {
        if (!StringUtils.hasText(op)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter operator required");
        }
        String normalized = op.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "=" -> "EQ";
            case "<" -> "LT";
            case ">" -> "GT";
            default -> normalized;
        };
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

    private long safeLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private static class TemplateContext {
        private final long templateId;
        private final String name;
        private final String baseEntity;
        private final List<String> columns;
        private final Map<String, TemplateFilterDefinition> userFilters;
        private final List<ReportFilter> fixedFilters;
        private final ReportTemplateResponse template;

        private TemplateContext(long templateId,
                                String name,
                                String baseEntity,
                                List<String> columns,
                                Map<String, TemplateFilterDefinition> userFilters,
                                List<ReportFilter> fixedFilters,
                                ReportTemplateResponse template) {
            this.templateId = templateId;
            this.name = name;
            this.baseEntity = baseEntity;
            this.columns = columns;
            this.userFilters = userFilters;
            this.fixedFilters = fixedFilters;
            this.template = template;
        }

        static TemplateContext from(ReportTemplateResponse template, DatePresetService datePresetService) {
            if (template == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
            }
            DynamicReportRequest request = template.getRequest();
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Template is missing report definition");
            }
            String baseEntity = requireNonBlank(request.getBaseEntity(), "Template base entity missing");
            List<String> columns = new ArrayList<>(Objects.requireNonNullElse(request.getColumns(), List.<String>of()));
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

                TemplateFilterDefinition definition = toDefinition(filter, template.getName(), datePresetService);
                if (treatAsUserInput) {
                    if (userFilters.containsKey(definition.lookupKey())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Duplicate filter key in template: " + definition.originalKey());
                    }
                    userFilters.put(definition.lookupKey(), definition);
                } else if (StringUtils.hasText(filter.getValue())) {
                    String storedOp = normalizeOpCode(filter.getOp());
                    if (!definition.allowedOps().contains(storedOp)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Fixed filter operator not allowed for " + definition.label());
                    }
                    ReportFilter fixed = new ReportFilter();
                    fixed.setKey(definition.originalKey());
                    fixed.setOp(storedOp);
                    fixed.setValue(filter.getValue());
                    fixed.setDataType(definition.dataType());
                    fixedFilters.add(fixed);
                }
            }
            return new TemplateContext(template.getId(), template.getName(), baseEntity, columns, userFilters, fixedFilters, template);
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

        long templateId() {
            return templateId;
        }

        String name() {
            return name;
        }

        ReportTemplateResponse template() {
            return template;
        }

        ReportExecutionModels.TemplateDetail toDetail() {
            List<ReportExecutionModels.FilterField> filters = userFilters.values().stream()
                    .map(def -> new ReportExecutionModels.FilterField(
                            def.originalKey(),
                            def.label(),
                            def.type(),
                            def.allowedOps(),
                            def.dateFormat(),
                            def.presetEnabled(),
                            def.presets()
                    ))
                    .toList();
            return new ReportExecutionModels.TemplateDetail(templateId, name, filters, template.getRequest().getMail());
        }

        private static TemplateFilterDefinition toDefinition(ReportFilter filter,
                                                             String reportCode,
                                                             DatePresetService datePresetService) {
            String originalKey = filter.getKey().trim();
            String lookupKey = normalizeKey(filter.getKey());
            ReportExecutionModels.FieldType type = resolveType(filter);
            String label = deriveLabel(filter.getLabel(), originalKey);
            List<String> allowedOps = resolveAllowedOps(filter, type);
            String dateFormat = type == ReportExecutionModels.FieldType.DATE ? DEFAULT_DATE_FORMAT : null;
            List<ReportExecutionModels.DatePresetOption> presets = type == ReportExecutionModels.FieldType.DATE
                    ? datePresetService.listPresetsForFilter(reportCode, originalKey)
                    : List.of();
            boolean presetEnabled = type == ReportExecutionModels.FieldType.DATE && !presets.isEmpty();

            return new TemplateFilterDefinition(originalKey, lookupKey, label, type, allowedOps,
                    dateFormat, normalizeDataType(type), presetEnabled, presets);
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

        private static List<String> resolveAllowedOps(ReportFilter filter, ReportExecutionModels.FieldType type) {
            if (filter.getAllowedOperators() != null && !filter.getAllowedOperators().isEmpty()) {
                LinkedHashSet<String> normalized = filter.getAllowedOperators().stream()
                        .filter(Objects::nonNull)
                        .map(Enum::name)
                        .map(String::toUpperCase)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                switch (type) {
                    case NUMBER -> normalized.addAll(NUMBER_OPS);
                    case DATE -> normalized.addAll(DATE_OPS);
                    case STRING -> normalized.retainAll(STRING_OPS);
                }
                return List.copyOf(normalized);
            }
            return switch (type) {
                case NUMBER -> NUMBER_OPS;
                case DATE -> DATE_OPS;
                case STRING -> STRING_OPS;
            };
        }

        private static ReportExecutionModels.FieldType resolveType(ReportFilter filter) {
            if (filter.getLogicalType() != null) {
                return switch (filter.getLogicalType()) {
                    case NUMBER -> ReportExecutionModels.FieldType.NUMBER;
                    case DATE -> ReportExecutionModels.FieldType.DATE;
                    case STRING -> ReportExecutionModels.FieldType.STRING;
                };
            }
            String dataType = filter.getDataType();
            if (!StringUtils.hasText(dataType)) {
                return ReportExecutionModels.FieldType.STRING;
            }
            String normalized = dataType.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "NUMBER" -> ReportExecutionModels.FieldType.NUMBER;
                case "DATE" -> ReportExecutionModels.FieldType.DATE;
                default -> ReportExecutionModels.FieldType.STRING;
            };
        }

        private static String normalizeDataType(ReportExecutionModels.FieldType type) {
            return switch (type) {
                case NUMBER -> "NUMBER";
                case DATE -> "DATE";
                case STRING -> "STRING";
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
            String dataType,
            boolean presetEnabled,
            List<ReportExecutionModels.DatePresetOption> presets
    ) {
    }
}
