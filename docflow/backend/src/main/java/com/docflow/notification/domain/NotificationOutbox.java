package com.docflow.notification.domain;

import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationEventType;
import com.docflow.notification.model.NotificationOutboxStatus;
import com.docflow.notification.model.NotificationReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_outbox")
@SequenceGenerator(name = "notification_outbox_seq", sequenceName = "notification_outbox_seq", allocationSize = 1)
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_outbox_seq")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 100)
    private NotificationEventCode eventCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 200)
    private String referenceId;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error", length = 4000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public Long getId() {
        return id;
    }

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

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public NotificationOutboxStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationOutboxStatus status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
