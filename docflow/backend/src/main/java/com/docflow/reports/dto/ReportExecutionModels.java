package com.docflow.reports.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReportExecutionModels {

    public static final String REPORT_MAIL_FIELD_TO = "to";
    public static final String REPORT_MAIL_FIELD_CC = "cc";
    public static final String REPORT_MAIL_FIELD_SUBJECT = "subject";
    public static final String REPORT_MAIL_FIELD_BODY = "body";
    public static final String REPORT_MAIL_FIELD_DISCLAIMER = "disclaimer";

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

    public record TemplateDetail(long templateId, String name, List<FilterField> filters, ReportMailConfig mail) {
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
        private String op;

        private String value;
        private String valueFrom;
        private String valueTo;
        private List<String> values = new ArrayList<>();

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

    public record RunResponse(List<String> columns, List<java.util.Map<String, Object>> rows, long rowCount) {
    }

    public enum ReportMailDeliveryMode {
        INLINE,
        ATTACHMENT
    }

    public static class MailRequest {
        @NotNull
        private Long templateId;

        @Valid
        private List<RunFilter> filters = new ArrayList<>();

        private String filterSummary;
        private ReportMailDeliveryMode deliveryMode;
        @Valid
        private MailFields fields = new MailFields();

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

        public String getFilterSummary() {
            return filterSummary;
        }

        public void setFilterSummary(String filterSummary) {
            this.filterSummary = filterSummary;
        }

        public ReportMailDeliveryMode getDeliveryMode() {
            return deliveryMode;
        }

        public void setDeliveryMode(ReportMailDeliveryMode deliveryMode) {
            this.deliveryMode = deliveryMode;
        }

        public MailFields getFields() {
            return fields;
        }

        public void setFields(MailFields fields) {
            this.fields = fields != null ? fields : new MailFields();
        }
    }

    public record MailResponse(String status, String message) {
    }

    public static class MailFields {
        private String to;
        private String cc;
        private String subject;
        private String body;
        private String disclaimer;

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getCc() {
            return cc;
        }

        public void setCc(String cc) {
            this.cc = cc;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getDisclaimer() {
            return disclaimer;
        }

        public void setDisclaimer(String disclaimer) {
            this.disclaimer = disclaimer;
        }
    }
}
