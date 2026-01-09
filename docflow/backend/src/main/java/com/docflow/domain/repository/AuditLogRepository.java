package com.docflow.domain.repository;

import com.docflow.domain.AuditLog;
import com.docflow.domain.AuditCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByDocumentIdOrderByChangedAtAsc(Long documentId);

    List<AuditLog> findByDocumentIdAndAuditCategoryOrderByChangedAtAsc(Long documentId, AuditCategory auditCategory);
}
