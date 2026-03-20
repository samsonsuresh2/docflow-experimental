package com.docflow.domain.repository;

import com.docflow.domain.AuditCategory;
import com.docflow.domain.DocumentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentAuditLogRepository extends JpaRepository<DocumentAuditLog, Long> {

    List<DocumentAuditLog> findByDocument_IdOrderByChangedAtAsc(Long documentId);

    List<DocumentAuditLog> findByDocument_IdAndAuditCategoryOrderByChangedAtAsc(Long documentId, AuditCategory auditCategory);
}
