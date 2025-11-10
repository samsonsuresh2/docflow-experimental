package com.docflow.reports.web;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {

    private final ReportMetadataService metadataService;
    private final DynamicReportBuilder builder;
    private final DynamicReportExecutor executor;
    private final ReportTemplateService templateService;

    public ReportController(ReportMetadataService metadataService,
                            DynamicReportBuilder builder,
                            DynamicReportExecutor executor,
                            ReportTemplateService templateService) {
        this.metadataService = metadataService;
        this.builder = builder;
        this.executor = executor;
        this.templateService = templateService;
    }

    @GetMapping("/meta")
    public Object metadata(@RequestParam(value = "entity", required = false) String entity) {
        if (entity == null || entity.isBlank()) {
            return new EntityListResponse(metadataService.listEntities());
        }
        return metadataService.getMetadata(entity);
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
    public ReportTemplateResponse saveTemplate(@Valid @RequestBody ReportTemplateRequest request,
                                               @RequestHeader(value = "X-USER-ID", required = false) String userId) {
        return templateService.save(request.getName(), request.getRequest(), userId);
    }

    @GetMapping("/templates")
    public TemplateListResponse listTemplates() {
        List<ReportTemplateResponse> templates = templateService.listTemplates();
        return new TemplateListResponse(templates);
    }
}

record EntityListResponse(java.util.List<String> entities) {
}

record TemplateListResponse(java.util.List<ReportTemplateResponse> templates) {
}
