package com.docflow.reports.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReportTemplateRequest {

    @NotBlank
    private String name;

    @NotNull
    @Valid
    private DynamicReportRequest request;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DynamicReportRequest getRequest() {
        return request;
    }

    public void setRequest(DynamicReportRequest request) {
        this.request = request;
    }
}
