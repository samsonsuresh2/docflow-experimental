package com.docflow.notification.model;

public class NotificationContent {

    private String subjectTemplate;
    private String bodyTemplate;
    private boolean html;

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(String subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public boolean isHtml() {
        return html;
    }

    public void setHtml(boolean html) {
        this.html = html;
    }
}
