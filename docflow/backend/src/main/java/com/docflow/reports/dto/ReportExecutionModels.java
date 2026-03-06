package com.docflow.reports.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReportExecutionModels {

    private ReportExecutionModels() {
        // utility container
    }

    public enum FieldType {
        STRING,
        NUMBER,
        DATE
    }

    public record TemplateSummary(long id, String name, String description) {
    }

    public record TemplateDetail(long templateId, String name, List<FilterField> filters) {
    }

    public record FilterField(
            String key,
            String label,
            FieldType type,
            List<String> allowedOps,
            String dateFormat,
            boolean presetEnabled,
            List<DatePresetOption> presets
    ) {
    }

    public record DatePresetOption(String code, String name, int displayOrder) {
    }

    public static class RunRequest {
        @NotNull
        private Long templateId;

        @Valid
        private List<RunFilter> filters = new ArrayList<>();

        public Long getTemplateId() {
            return templateId;
        }

        public void setTemplateId(Long templateId) {
            this.templateId = templateId;
        }

        public List<RunFilter> getFilters() {
            return filters;
        }

        public void setFilters(List<RunFilter> filters) {
            this.filters = filters != null ? filters : new ArrayList<>();
        }
    }

    public static class RunFilter {
        @NotNull
        private String key;

        @NotNull
        private String op;

        private String value;

        private DateFilterMode mode;

        private String fromValue;

        private String toValue;

        private String presetCode;

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

        public DateFilterMode getMode() {
            return mode;
        }

        public void setMode(DateFilterMode mode) {
            this.mode = mode;
        }

        public String getFromValue() {
            return fromValue;
        }

        public void setFromValue(String fromValue) {
            this.fromValue = fromValue;
        }

        public String getToValue() {
            return toValue;
        }

        public void setToValue(String toValue) {
            this.toValue = toValue;
        }

        public String getPresetCode() {
            return presetCode;
        }

        public void setPresetCode(String presetCode) {
            this.presetCode = presetCode;
        }
    }

    public enum DateFilterMode {
        MANUAL,
        PRESET
    }

    public record RunResponse(List<String> columns, List<java.util.Map<String, Object>> rows, int rowCount) {
    }
}
