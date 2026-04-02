package com.docflow.notification.model;

import java.util.ArrayList;
import java.util.List;

public class NotificationMessage {

    private NotificationChannelType channelType = NotificationChannelType.EMAIL;
    private List<String> to = new ArrayList<>();
    private List<String> cc = new ArrayList<>();
    private String subject;
    private String body;
    private boolean html;
    private NotificationAttachment attachment;

    public NotificationChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(NotificationChannelType channelType) {
        this.channelType = channelType;
    }

    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to != null ? new ArrayList<>(to) : new ArrayList<>();
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc != null ? new ArrayList<>(cc) : new ArrayList<>();
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

    public boolean isHtml() {
        return html;
    }

    public void setHtml(boolean html) {
        this.html = html;
    }

    public NotificationAttachment getAttachment() {
        return attachment;
    }

    public void setAttachment(NotificationAttachment attachment) {
        this.attachment = attachment;
    }
}
