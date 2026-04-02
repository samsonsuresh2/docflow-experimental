package com.docflow.reports.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportMailFieldConfig {

    private String mandatory;

    @JsonProperty("default")
    private String defaultValue;

    private boolean editable = true;

    public String getMandatory() {
        return mandatory;
    }

    public void setMandatory(String mandatory) {
        this.mandatory = mandatory;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
}
