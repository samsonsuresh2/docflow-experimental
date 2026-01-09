package com.docflow.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_log")
@SequenceGenerator(name = "audit_log_seq", sequenceName = "audit_log_seq", allocationSize = 1)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private DocumentParent document;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_category")
    private AuditCategory auditCategory;

    @Column(name = "event_code")
    private String eventCode;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "comment_text")
    private String comment;

    @Column(name = "field_key")
    private String fieldKey;

    @Lob
    @Column(name = "old_value")
    private String oldValue;

    @Lob
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "change_type")
    private String changeType;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    public Long getId() {
        return id;
    }

    public DocumentParent getDocument() {
        return document;
    }

    public void setDocument(DocumentParent document) {
        this.document = document;
    }

    public AuditCategory getAuditCategory() {
        return auditCategory;
    }

    public void setAuditCategory(AuditCategory auditCategory) {
        this.auditCategory = auditCategory;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(OffsetDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
