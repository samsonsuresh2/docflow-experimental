package com.docflow.reports.dto;

import jakarta.validation.constraints.NotBlank;

public class ReportJoin {

    @NotBlank
    private String rightEntity;

    @NotBlank
    private String on;

    public String getRightEntity() {
        return rightEntity;
    }

    public void setRightEntity(String rightEntity) {
        this.rightEntity = rightEntity;
    }

    public String getOn() {
        return on;
    }

    public void setOn(String on) {
        this.on = on;
    }
}
