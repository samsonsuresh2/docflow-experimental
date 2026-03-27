package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AuditCategory;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DefaultAuditService implements AuditService {

    private final DocumentAuditLogRepository documentAuditLogRepository;
    private final ObjectMapper objectMapper;

    public DefaultAuditService(DocumentAuditLogRepository documentAuditLogRepository, ObjectMapper objectMapper) {
        this.documentAuditLogRepository = documentAuditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void logFieldUpdate(DocumentParent document, String fieldKey, Object oldValue, Object newValue, String changeType,
                               RequestUser user, OffsetDateTime when) {
        DocumentAuditLog log = new DocumentAuditLog();
        log.setDocument(document);
        log.setAuditCategory(AuditCategory.FIELD_CHANGE);
        log.setFieldKey(fieldKey);
        log.setOldValue(serialize(oldValue));
        log.setNewValue(serialize(newValue));
        log.setChangeType(changeType != null ? changeType : "UPDATED");
        log.setChangedBy(user.userId());
        log.setChangedAt(when != null ? when : OffsetDateTime.now());
        documentAuditLogRepository.save(log);
    }

    @Override
    public void logStatusChange(DocumentParent document, DocumentStatus previousStatus, DocumentStatus newStatus, String action,
                                String comment, RequestUser user, OffsetDateTime when) {
        Map<String, Object> oldPayload = new LinkedHashMap<>();
        oldPayload.put("status", previousStatus != null ? previousStatus.name() : null);

        Map<String, Object> newPayload = new LinkedHashMap<>();
        newPayload.put("status", newStatus != null ? newStatus.name() : null);
        if (comment != null && !comment.isBlank()) {
            newPayload.put("comment", comment);
        }

        logFieldUpdate(document, "status", oldPayload, newPayload,
                action != null ? action : "STATUS", user, when);
    }

    @Override
    public void logLifecycleEvent(DocumentParent document, DocumentStatus previousStatus, DocumentStatus newStatus, String eventCode,
                                  String comment, RequestUser user, OffsetDateTime when) {
        DocumentAuditLog log = new DocumentAuditLog();
        log.setDocument(document);
        log.setAuditCategory(AuditCategory.LIFECYCLE);
        log.setEventCode(eventCode);
        log.setFromStatus(previousStatus != null ? previousStatus.name() : null);
        log.setToStatus(newStatus != null ? newStatus.name() : null);
        log.setComment(comment != null && !comment.isBlank() ? comment : null);
        log.setChangedBy(user.userId());
        log.setChangedAt(when != null ? when : OffsetDateTime.now());
        documentAuditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAuditLog> getAuditTrail(Long documentId) {
        return documentAuditLogRepository.findByDocument_IdOrderByChangedAtAsc(documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAuditLog> getLifecycleTimeline(Long documentId) {
        return documentAuditLogRepository.findByDocument_IdAndAuditCategoryOrderByChangedAtAsc(
            documentId,
            AuditCategory.LIFECYCLE
        );
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize audit payload", e);
        }
    }
}
