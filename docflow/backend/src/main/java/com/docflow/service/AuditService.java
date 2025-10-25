package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Transactional
public interface AuditService {

    @Transactional
    void logFieldUpdate(DocumentParent document, String fieldKey, Object oldValue, Object newValue, String changeType,
                        RequestUser user, OffsetDateTime when);

    @Transactional
    void logStatusChange(DocumentParent document, DocumentStatus previousStatus, DocumentStatus newStatus, String action,
                         String comment, RequestUser user, OffsetDateTime when);

    @Transactional(readOnly = true)
    List<AuditLog> getAuditTrail(Long documentId);
}
