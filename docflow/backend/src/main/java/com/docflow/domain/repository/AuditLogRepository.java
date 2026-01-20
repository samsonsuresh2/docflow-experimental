package com.docflow.domain.repository;

import com.docflow.domain.AuditLog;
import com.docflow.domain.AuditCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByDocument_IdOrderByChangedAtAsc(Long documentId);

    List<AuditLog> findByDocument_IdAndAuditCategoryOrderByChangedAtAsc(Long documentId, AuditCategory auditCategory);
}
