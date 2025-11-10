package com.docflow.reports.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public class ReportTemplateResponse {

    private long id;
    private String name;
    private DynamicReportRequest request;
    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    public ReportTemplateResponse() {
    }

    public ReportTemplateResponse(long id, String name, DynamicReportRequest request, String createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.request = request;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
