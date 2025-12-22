package com.docflow.reports.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DynamicReportRequest {

    @NotBlank
    private String baseEntity;

    @NotNull
    private List<String> columns = new ArrayList<>();

    @Valid
    private List<ReportFilter> filters = new ArrayList<>();

    @Valid
    private List<ReportJoin> joins = new ArrayList<>();

    public String getBaseEntity() {
        return baseEntity;
    }

    public void setBaseEntity(String baseEntity) {
        this.baseEntity = baseEntity;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns != null ? columns : new ArrayList<>();
    }

    public List<ReportFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<ReportFilter> filters) {
        this.filters = filters != null ? filters : new ArrayList<>();
    }

    public List<ReportJoin> getJoins() {
        return joins;
    }

    public void setJoins(List<ReportJoin> joins) {
        this.joins = joins != null ? joins : new ArrayList<>();
    }
}
