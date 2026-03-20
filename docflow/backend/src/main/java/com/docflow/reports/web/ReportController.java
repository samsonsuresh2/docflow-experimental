package com.docflow.reports.web;

import com.docflow.context.RequestUserContext;
import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportTemplateRequest;
import com.docflow.reports.dto.ReportTemplateResponse;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {

    private final ReportMetadataService metadataService;
    private final DynamicReportBuilder builder;
    private final DynamicReportExecutor executor;
    private final ReportTemplateService templateService;
    private final ReportProperties properties;
    private final RequestUserContext requestUserContext;

    public ReportController(ReportMetadataService metadataService,
                            DynamicReportBuilder builder,
                            DynamicReportExecutor executor,
                            ReportTemplateService templateService,
                            ReportProperties properties,
                            RequestUserContext requestUserContext) {
        this.metadataService = metadataService;
        this.builder = builder;
        this.executor = executor;
        this.templateService = templateService;
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
        return new AdminScopeResponse(baseEntities, baseColumns, documentColumns, metadataKeys);
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> run(@Valid @RequestBody DynamicReportRequest request,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        var built = builder.build(request);
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
}

record TemplateListResponse(java.util.List<ReportTemplateResponse> templates) {
}

record AdminScopeResponse(List<ReportMetadataService.BaseEntity> entities,
                          List<String> baseColumns,
                          List<String> documentColumns,
                          List<String> metadataKeys) {
}
