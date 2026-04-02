package com.docflow.notification.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class NotificationEvent {

    private NotificationEventType eventType;
    private NotificationEventCode eventCode;
    private NotificationReferenceType referenceType;
    private String referenceId;
    private NotificationChannelType channelType = NotificationChannelType.EMAIL;
    private String templateCode;
    private NotificationContent contentOverride;
    private NotificationExplicitRecipients explicitRecipients = new NotificationExplicitRecipients();
    private NotificationAttachment attachment;
    private Map<String, Object> context = new LinkedHashMap<>();

    public NotificationEventType getEventType() {
        return eventType;
    }

    public void setEventType(NotificationEventType eventType) {
        this.eventType = eventType;
    }

    public NotificationEventCode getEventCode() {
        return eventCode;
    }

    public void setEventCode(NotificationEventCode eventCode) {
        this.eventCode = eventCode;
    }

    public NotificationReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(NotificationReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public NotificationChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(NotificationChannelType channelType) {
        this.channelType = channelType;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public NotificationContent getContentOverride() {
        return contentOverride;
    }

    public void setContentOverride(NotificationContent contentOverride) {
        this.contentOverride = contentOverride;
    }

    public NotificationExplicitRecipients getExplicitRecipients() {
        return explicitRecipients;
    }

    public void setExplicitRecipients(NotificationExplicitRecipients explicitRecipients) {
        this.explicitRecipients = explicitRecipients != null ? explicitRecipients : new NotificationExplicitRecipients();
    }

    public NotificationAttachment getAttachment() {
        return attachment;
    }

    public void setAttachment(NotificationAttachment attachment) {
        this.attachment = attachment;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context != null ? new LinkedHashMap<>(context) : new LinkedHashMap<>();
    }
}
