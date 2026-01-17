package com.docflow.api.dto;

import java.time.OffsetDateTime;

public class FastTrackDecisionRequest {

    private String documentId;
    private String decision;
    private String comment;
    private OffsetDateTime expectedUpdatedAt;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public OffsetDateTime getExpectedUpdatedAt() {
        return expectedUpdatedAt;
    }

    public void setExpectedUpdatedAt(OffsetDateTime expectedUpdatedAt) {
        this.expectedUpdatedAt = expectedUpdatedAt;
    }
}
