package com.docflow.notification.domain;

import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationRecipientType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class NotificationRecipientPolicyId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 100)
    private NotificationEventCode eventCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 50)
    private NotificationRecipientType recipientType;

    public NotificationRecipientPolicyId() {
    }

    public NotificationRecipientPolicyId(NotificationEventCode eventCode, NotificationRecipientType recipientType) {
        this.eventCode = eventCode;
        this.recipientType = recipientType;
    }

    public NotificationEventCode getEventCode() {
        return eventCode;
    }

    public void setEventCode(NotificationEventCode eventCode) {
        this.eventCode = eventCode;
    }

    public NotificationRecipientType getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(NotificationRecipientType recipientType) {
        this.recipientType = recipientType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationRecipientPolicyId that)) {
            return false;
        }
        return eventCode == that.eventCode && recipientType == that.recipientType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventCode, recipientType);
    }
}
