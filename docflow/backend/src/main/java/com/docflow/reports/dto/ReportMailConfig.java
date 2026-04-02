package com.docflow.reports.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;

public class ReportMailConfig {

    @JsonAlias("mailEnabled")
    private boolean enabled;

    @JsonAlias("mailMode")
    private ReportMailMode mode = ReportMailMode.INLINE_ONLY;

    private ReportMailAttachmentFormat attachmentFormat = ReportMailAttachmentFormat.CSV;

    @Valid
    private ReportMailFieldConfig to = new ReportMailFieldConfig();

    @Valid
    private ReportMailFieldConfig cc = new ReportMailFieldConfig();

    @Valid
    private ReportMailFieldConfig subject = new ReportMailFieldConfig();

    @Valid
    private ReportMailFieldConfig body = new ReportMailFieldConfig();

    @Valid
    private ReportMailFieldConfig disclaimer = new ReportMailFieldConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMailEnabled() {
        return enabled;
    }

    public void setMailEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ReportMailMode getMode() {
        return mode;
    }

    public void setMode(ReportMailMode mode) {
        this.mode = mode != null ? mode : ReportMailMode.INLINE_ONLY;
    }

    public ReportMailMode getMailMode() {
        return mode;
    }

    public void setMailMode(ReportMailMode mode) {
        this.mode = mode != null ? mode : ReportMailMode.INLINE_ONLY;
    }

    public ReportMailAttachmentFormat getAttachmentFormat() {
        return attachmentFormat;
    }

    public void setAttachmentFormat(ReportMailAttachmentFormat attachmentFormat) {
        this.attachmentFormat = attachmentFormat != null ? attachmentFormat : ReportMailAttachmentFormat.CSV;
    }

    public ReportMailFieldConfig getTo() {
        return to;
    }

    public void setTo(ReportMailFieldConfig to) {
        this.to = to != null ? to : new ReportMailFieldConfig();
    }

    public ReportMailFieldConfig getCc() {
        return cc;
    }

    public void setCc(ReportMailFieldConfig cc) {
        this.cc = cc != null ? cc : new ReportMailFieldConfig();
    }

    public ReportMailFieldConfig getSubject() {
        return subject;
    }

    public void setSubject(ReportMailFieldConfig subject) {
        this.subject = subject != null ? subject : new ReportMailFieldConfig();
    }

    public ReportMailFieldConfig getBody() {
        return body;
    }

    public void setBody(ReportMailFieldConfig body) {
        this.body = body != null ? body : new ReportMailFieldConfig();
    }

    public ReportMailFieldConfig getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(ReportMailFieldConfig disclaimer) {
        this.disclaimer = disclaimer != null ? disclaimer : new ReportMailFieldConfig();
    }
}
