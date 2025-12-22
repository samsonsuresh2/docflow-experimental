package com.docflow.reports.dto;

import jakarta.validation.constraints.NotBlank;

public class ReportFilter {

    @NotBlank
    private String key;

    @NotBlank
    private String op;

    @NotBlank
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
