package com.docflow.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "document_metadata",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_metadata_document_key", columnNames = {"document_id", "field_key"}))
@SequenceGenerator(name = "document_metadata_seq", sequenceName = "document_metadata_seq", allocationSize = 1)
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "document_metadata_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private DocumentParent document;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Lob
    @Column(name = "field_value")
    private String fieldValue;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public DocumentParent getDocument() {
        return document;
    }

    public void setDocument(DocumentParent document) {
        this.document = document;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
