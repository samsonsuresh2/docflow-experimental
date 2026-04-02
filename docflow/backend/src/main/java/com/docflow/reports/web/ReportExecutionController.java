package com.docflow.reports.web;

import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.service.ReportExecutionService;
import com.docflow.reports.service.ReportMailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final ReportMailService reportMailService;

    public ReportExecutionController(ReportExecutionService executionService,
                                     ReportMailService reportMailService) {
        this.executionService = executionService;
        this.reportMailService = reportMailService;
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

    @PostMapping(value = "/mail", params = "mode=exec")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public ReportExecutionModels.MailResponse mail(@Valid @RequestBody ReportExecutionModels.MailRequest request) {
        return reportMailService.enqueue(request);
    }
}

record ExecutableTemplateListResponse(List<ReportExecutionModels.TemplateSummary> templates) {
}
