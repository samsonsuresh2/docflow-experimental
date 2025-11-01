package com.docflow.service;

import com.docflow.api.dto.DocumentResponse;
import com.docflow.api.dto.DocumentUploadMetadata;
import com.docflow.context.RequestUser;
import com.docflow.domain.AuditLog;
import com.docflow.domain.DocumentStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Transactional
public interface DocumentService {

    @Transactional
    DocumentResponse createDocument(DocumentUploadMetadata metadata, MultipartFile file, RequestUser user);

    @Transactional(readOnly = true)
    DocumentResponse getDocument(Long id);

    @Transactional
    DocumentResponse submitDocument(Long id, RequestUser user);

    @Transactional
    DocumentResponse updateStatus(Long id, DocumentStatus status, RequestUser user, String action, String comment);

    @Transactional
    DocumentResponse updateMetadata(Long id, Map<String, Object> requestedMetadata, RequestUser user);

    @Transactional(readOnly = true)
    List<AuditLog> getAuditTrail(Long id);

    @Transactional(readOnly = true)
    DocumentFile getDocumentFile(Long id);

    @Transactional
    DocumentResponse reject(Long id, RequestUser user, String comment);

    @Transactional
    DocumentResponse approve(Long id, RequestUser user, String comment);

    @Transactional
    DocumentResponse rework(Long id, RequestUser user, String comment);

    @Transactional
    DocumentResponse close(Long id, RequestUser user, String comment);

    @Transactional
    DocumentResponse moveToUnderReview(Long id, RequestUser user, String comment);
}
