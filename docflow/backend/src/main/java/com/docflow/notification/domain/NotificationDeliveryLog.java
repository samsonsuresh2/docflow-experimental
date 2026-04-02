package com.docflow.notification.domain;

import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationDeliveryStatus;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_delivery_log")
@SequenceGenerator(name = "notification_delivery_log_seq", sequenceName = "notification_delivery_log_seq", allocationSize = 1)
public class NotificationDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_delivery_log_seq")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 100)
    private NotificationEventCode eventCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 200)
    private String referenceId;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 50)
    private NotificationChannelType channelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NotificationDeliveryStatus status;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
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

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public NotificationChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(NotificationChannelType channelType) {
        this.channelType = channelType;
    }

    public NotificationDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationDeliveryStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
