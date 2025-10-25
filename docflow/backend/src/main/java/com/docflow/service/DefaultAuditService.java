package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.AuditLogRepository;
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

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public DefaultAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void logFieldUpdate(DocumentParent document, String fieldKey, Object oldValue, Object newValue, String changeType,
                               RequestUser user, OffsetDateTime when) {
        AuditLog log = new AuditLog();
        log.setDocument(document);
        log.setFieldKey(fieldKey);
        log.setOldValue(serialize(oldValue));
        log.setNewValue(serialize(newValue));
        log.setChangeType(changeType != null ? changeType : "UPDATED");
        log.setChangedBy(user.userId());
        log.setChangedAt(when != null ? when : OffsetDateTime.now());
        auditLogRepository.save(log);
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
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail(Long documentId) {
        return auditLogRepository.findByDocumentIdOrderByChangedAtAsc(documentId);
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
