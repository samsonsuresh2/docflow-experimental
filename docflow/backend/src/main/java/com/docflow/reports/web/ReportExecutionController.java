package com.docflow.reports.web;

import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.service.ReportExecutionService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportExecutionController {

    private final ReportExecutionService executionService;

    public ReportExecutionController(ReportExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping(value = "/templates", params = "mode=exec")
    public ExecutableTemplateListResponse listExecutableTemplates() {
        List<ReportExecutionModels.TemplateSummary> templates = executionService.listExecutableTemplates();
        return new ExecutableTemplateListResponse(templates);
    }

    @GetMapping("/templates/{id}")
    public ReportExecutionModels.TemplateDetail getTemplate(@PathVariable("id") long templateId) {
        return executionService.getExecutableTemplate(templateId);
    }

    @PostMapping(value = "/run", params = "mode=exec")
    public ReportExecutionModels.RunResponse run(
            @Valid @RequestBody ReportExecutionModels.RunRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return executionService.run(request, page, size);
    }
}

record ExecutableTemplateListResponse(List<ReportExecutionModels.TemplateSummary> templates) {
}
