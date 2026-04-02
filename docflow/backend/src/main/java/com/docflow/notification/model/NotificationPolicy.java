package com.docflow.notification.model;

import java.util.EnumMap;
import java.util.Map;

public class NotificationPolicy {

    private boolean enabled;
    private NotificationChannelType channelType = NotificationChannelType.EMAIL;
    private String templateCode;
    private boolean sendForBulk;
    private Map<NotificationRecipientType, Boolean> recipientPolicies = new EnumMap<>(NotificationRecipientType.class);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public boolean isSendForBulk() {
        return sendForBulk;
    }

    public void setSendForBulk(boolean sendForBulk) {
        this.sendForBulk = sendForBulk;
    }

    public Map<NotificationRecipientType, Boolean> getRecipientPolicies() {
        return recipientPolicies;
    }

    public void setRecipientPolicies(Map<NotificationRecipientType, Boolean> recipientPolicies) {
        EnumMap<NotificationRecipientType, Boolean> copy = new EnumMap<>(NotificationRecipientType.class);
        if (recipientPolicies != null) {
            copy.putAll(recipientPolicies);
        }
        this.recipientPolicies = copy;
    }

    public boolean isRecipientEnabled(NotificationRecipientType recipientType) {
        return recipientPolicies.getOrDefault(recipientType, Boolean.FALSE);
    }
}
