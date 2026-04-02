package com.docflow.notification.model;

import java.util.ArrayList;
import java.util.List;

public class NotificationExplicitRecipients {

    private List<String> to = new ArrayList<>();
    private List<String> cc = new ArrayList<>();

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
}
