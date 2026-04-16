package com.docflow.reports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReportFilter {

    public enum Mode {
        FIXED_VALUE,
        USER_INPUT
    }

    @NotBlank
    private String key;

    @NotBlank
    private String op;

    private String value;
    private String valueFrom;
    private String valueTo;
    private List<String> values = new ArrayList<>();

    @NotNull
    private Mode mode = Mode.FIXED_VALUE;

    private String label;

    private String dataType;

    private FilterSourceType source;

    private String field;

    private FilterLogicalType logicalType;

    private List<FilterOperator> allowedOperators = new ArrayList<>();

    private List<String> presetCodes = new ArrayList<>();

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

    public String getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(String valueFrom) {
        this.valueFrom = valueFrom;
    }

    public String getValueTo() {
        return valueTo;
    }

    public void setValueTo(String valueTo) {
        this.valueTo = valueTo;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values != null ? values : new ArrayList<>();
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public FilterSourceType getSource() {
        return source;
    }

    public void setSource(FilterSourceType source) {
        this.source = source;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public FilterLogicalType getLogicalType() {
        return logicalType;
    }

    public void setLogicalType(FilterLogicalType logicalType) {
        this.logicalType = logicalType;
    }

    public List<FilterOperator> getAllowedOperators() {
        return allowedOperators;
    }

    public void setAllowedOperators(List<FilterOperator> allowedOperators) {
        this.allowedOperators = allowedOperators != null ? allowedOperators : new ArrayList<>();
    }

    public List<String> getPresetCodes() {
        return presetCodes;
    }

    public void setPresetCodes(List<String> presetCodes) {
        this.presetCodes = presetCodes != null ? presetCodes : new ArrayList<>();
    }

    public enum FilterSourceType {
        DOCUMENT,
        DOCUMENT_METADATA,
        THIRD_PARTY_ENTITY
    }

    public enum FilterLogicalType {
        STRING,
        NUMBER,
        DATE
    }

    public enum FilterOperator {
        EQ,
        LIKE,
        IN,
        NOT_IN,
        LT,
        GT,
        RANGE,
        BETWEEN
    }
}
