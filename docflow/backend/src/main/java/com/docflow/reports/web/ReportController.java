package com.docflow.reports.web;

import com.docflow.context.RequestUserContext;
import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportTemplateRequest;
import com.docflow.reports.dto.ReportTemplateResponse;
import com.docflow.reports.service.DatePresetService;
import com.docflow.reports.service.DynamicReportBuilder;
import com.docflow.reports.service.DynamicReportExecutor;
import com.docflow.reports.service.ReportMetadataService;
import com.docflow.reports.service.ReportTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {
    private static final DateTimeFormatter PRESET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportMetadataService metadataService;
    private final DynamicReportBuilder builder;
    private final DynamicReportExecutor executor;
    private final ReportTemplateService templateService;
    private final DatePresetService datePresetService;
    private final ReportProperties properties;
    private final RequestUserContext requestUserContext;

    public ReportController(ReportMetadataService metadataService,
                            DynamicReportBuilder builder,
                            DynamicReportExecutor executor,
                            ReportTemplateService templateService,
                            DatePresetService datePresetService,
                            ReportProperties properties,
                            RequestUserContext requestUserContext) {
        this.metadataService = metadataService;
        this.builder = builder;
        this.executor = executor;
        this.templateService = templateService;
        this.datePresetService = datePresetService;
        this.properties = properties;
        this.requestUserContext = requestUserContext;
    }

    @GetMapping("/admin/scope")
    public AdminScopeResponse adminScope(@RequestParam(value = "baseEntity", required = false) String baseEntity) {
        List<ReportMetadataService.BaseEntity> baseEntities = metadataService.listBaseEntities();
        List<String> baseColumns = baseEntity != null && !baseEntity.isBlank()
                ? metadataService.getColumns(baseEntity).columns()
                : List.of();
        List<String> documentColumns = metadataService.getColumns(properties.getDocumentTable().getName()).columns();
        List<String> metadataKeys = metadataService.listMetadataKeys();
        List<ReportExecutionModels.DatePresetOption> presets = datePresetService.listAvailablePresets();
        return new AdminScopeResponse(baseEntities, baseColumns, documentColumns, metadataKeys, presets);
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> run(@Valid @RequestBody DynamicReportRequest request,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        DynamicReportRequest normalizedRequest = normalizePresetPreviewRequest(request);
        com.docflow.reports.service.ReportRunPolicy.validateAtLeastOneRuntimeFilter(normalizedRequest.getFilters());
        var built = builder.build(normalizedRequest);
        return executor.execute(built, page, size);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportTemplateResponse saveTemplate(@Valid @RequestBody ReportTemplateRequest request) {
        return templateService.createTemplate(request.getName(), request.getRequest(), currentUserId());
    }

    @PutMapping("/templates/{id}")
    public ReportTemplateResponse updateTemplate(@PathVariable("id") long templateId,
                                                 @Valid @RequestBody ReportTemplateRequest request) {
        return templateService.update(templateId, request.getName(), request.getRequest(), currentUserId());
    }

    @GetMapping("/templates")
    public TemplateListResponse listTemplates() {
        List<ReportTemplateResponse> templates = templateService.listTemplates();
        return new TemplateListResponse(templates);
    }

    private String currentUserId() {
        return requestUserContext.getCurrentUser()
                .map(user -> user.userId())
                .orElse(null);
    }

    private DynamicReportRequest normalizePresetPreviewRequest(DynamicReportRequest request) {
        DynamicReportRequest normalized = new DynamicReportRequest();
        normalized.setBaseEntity(request.getBaseEntity());
        normalized.setColumns(new ArrayList<>(request.getColumns()));
        normalized.setJoins(new ArrayList<>(request.getJoins()));

        List<ReportFilter> filters = new ArrayList<>();
        for (ReportFilter filter : request.getFilters()) {
            if (filter == null) {
                continue;
            }
            boolean presetPreview = filter.getLogicalType() == ReportFilter.FilterLogicalType.DATE
                    && filter.getPresetCodes() != null
                    && !filter.getPresetCodes().isEmpty();
            if (!presetPreview) {
                filters.add(filter);
                continue;
            }

            DatePresetService.ResolvedDateRange range = datePresetService.resolveAvailablePreset(filter.getPresetCodes().get(0));
            filters.add(boundFilter(filter, "GE", range.fromDate().format(PRESET_DATE_FORMAT)));
            filters.add(boundFilter(filter, "LE", range.toDate().format(PRESET_DATE_FORMAT)));
        }
        normalized.setFilters(filters);
        return normalized;
    }

    private ReportFilter boundFilter(ReportFilter source, String op, String value) {
        ReportFilter filter = new ReportFilter();
        filter.setKey(source.getKey());
        filter.setOp(op);
        filter.setValue(value);
        filter.setMode(source.getMode());
        filter.setLabel(source.getLabel());
        filter.setDataType(source.getDataType());
        filter.setSource(source.getSource());
        filter.setField(source.getField());
        filter.setLogicalType(source.getLogicalType());
        filter.setAllowedOperators(source.getAllowedOperators());
        return filter;
    }
}

record TemplateListResponse(java.util.List<ReportTemplateResponse> templates) {
}

record AdminScopeResponse(List<ReportMetadataService.BaseEntity> entities,
                          List<String> baseColumns,
                          List<String> documentColumns,
                          List<String> metadataKeys,
                          List<ReportExecutionModels.DatePresetOption> presets) {
}
