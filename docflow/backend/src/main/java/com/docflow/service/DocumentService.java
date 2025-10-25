package com.docflow.service;

import com.docflow.api.dto.DocumentResponse;
import com.docflow.api.dto.DocumentUploadMetadata;
import com.docflow.context.RequestUser;
import com.docflow.domain.*;
import com.docflow.domain.repository.AuditLogRepository;
import com.docflow.domain.repository.DocumentMetadataRepository;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.storage.StorageAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMetadataRepository metadataRepository;
    private final AuditLogRepository auditLogRepository;
    private final StorageAdapter storageAdapter;
    private final ObjectMapper objectMapper;
    private final RuleService ruleService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentMetadataRepository metadataRepository,
                           AuditLogRepository auditLogRepository,
                           StorageAdapter storageAdapter,
                           ObjectMapper objectMapper,
                           RuleService ruleService) {
        this.documentRepository = documentRepository;
        this.metadataRepository = metadataRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageAdapter = storageAdapter;
        this.objectMapper = objectMapper;
        this.ruleService = ruleService;
    }

    @Transactional
    public DocumentResponse createDocument(DocumentUploadMetadata metadata, MultipartFile file, RequestUser user) {
        validateUniqueDocumentNumber(metadata.getDocumentNumber());

        DocumentParent document = new DocumentParent();
        document.setDocumentNumber(metadata.getDocumentNumber());
        document.setTitle(metadata.getTitle());
        document.setStatus(DocumentStatus.DRAFT);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(OffsetDateTime.now());
        DocumentParent saved = documentRepository.save(document);

        persistMetadata(saved, metadata.getMetadata(), user);

        storeFileIfPresent(saved, file);

        return mapToResponse(saved, fetchMetadataMap(saved));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id) {
        DocumentParent document = requireDocument(id);
        return mapToResponse(document, fetchMetadataMap(document));
    }

    @Transactional
    public DocumentResponse submitDocument(Long id, RequestUser user) {
        DocumentParent document = requireDocument(id);
        if (document.getStatus() == DocumentStatus.SUBMITTED || document.getStatus() == DocumentStatus.UNDER_REVIEW) {
            return mapToResponse(document, fetchMetadataMap(document));
        }
        DocumentStatus previousStatus = document.getStatus();
        document.setStatus(DocumentStatus.SUBMITTED);
        OffsetDateTime now = OffsetDateTime.now();
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);
        logStatusChange(document, previousStatus, DocumentStatus.SUBMITTED, user, "SUBMIT", null, now);
        return mapToResponse(document, fetchMetadataMap(document));
    }

    @Transactional
    public DocumentResponse updateStatus(Long id, DocumentStatus status, RequestUser user, String action, String comment) {
        DocumentParent document = requireDocument(id);
        DocumentStatus previousStatus = document.getStatus();
        if (Objects.equals(previousStatus, status)) {
            return mapToResponse(document, fetchMetadataMap(document));
        }
        document.setStatus(status);
        OffsetDateTime now = OffsetDateTime.now();
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);
        logStatusChange(document, previousStatus, status, user, action, comment, now);
        return mapToResponse(document, fetchMetadataMap(document));
    }

    @Transactional
    public DocumentResponse updateMetadata(Long id, Map<String, Object> requestedMetadata, RequestUser user) {
        DocumentParent document = requireDocument(id);
        List<DocumentMetadata> existing = metadataRepository.findByDocument(document);
        Map<String, DocumentMetadata> byKey = existing.stream()
                .collect(Collectors.toMap(DocumentMetadata::getFieldKey, m -> m));

        OffsetDateTime now = OffsetDateTime.now();
        Set<String> processed = new HashSet<>();
        for (Map.Entry<String, Object> entry : requestedMetadata.entrySet()) {
            String key = entry.getKey();
            String newValue = serializeValue(entry.getValue());
            DocumentMetadata current = byKey.get(key);
            if (current == null) {
                DocumentMetadata created = new DocumentMetadata();
                created.setDocument(document);
                created.setFieldKey(key);
                created.setFieldValue(newValue);
                created.setCreatedBy(user.userId());
                created.setCreatedAt(now);
                metadataRepository.save(created);
                createAuditLog(document, key, null, newValue, "ADDED", user, now);
            } else if (!Objects.equals(current.getFieldValue(), newValue)) {
                String oldValue = current.getFieldValue();
                current.setFieldValue(newValue);
                current.setCreatedBy(user.userId());
                current.setCreatedAt(now);
                metadataRepository.save(current);
                createAuditLog(document, key, oldValue, newValue, "UPDATED", user, now);
            }
            processed.add(key);
        }

        for (DocumentMetadata metadata : existing) {
            if (!processed.contains(metadata.getFieldKey())) {
                metadataRepository.delete(metadata);
                createAuditLog(document, metadata.getFieldKey(), metadata.getFieldValue(), null, "REMOVED", user, now);
            }
        }

        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        return mapToResponse(document, fetchMetadataMap(document));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail(Long id) {
        requireDocument(id);
        return auditLogRepository.findByDocumentIdOrderByChangedAtAsc(id);
    }

    @Transactional
    public DocumentResponse markForRework(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.REWORK, user, "REWORK", comment);
    }

    @Transactional
    public DocumentResponse approve(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.APPROVED, user, "APPROVE", comment);
    }

    @Transactional
    public DocumentResponse close(Long id, RequestUser user, String comment) {
        DocumentParent document = requireDocument(id);
        if (!ruleService.validateForClosure(document)) {
            throw new IllegalStateException("Document is not eligible for closure");
        }
        return updateStatus(id, DocumentStatus.CLOSED, user, "CLOSE", comment);
    }

    @Transactional
    public DocumentResponse moveToUnderReview(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.UNDER_REVIEW, user, "UNDER_REVIEW", comment);
    }

    private void validateUniqueDocumentNumber(String documentNumber) {
        documentRepository.findByDocumentNumber(documentNumber).ifPresent(doc -> {
            throw new IllegalStateException("Document number already exists");
        });
    }

    private DocumentParent requireDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
    }

    private void persistMetadata(DocumentParent document, Map<String, Object> metadata, RequestUser user) {
        OffsetDateTime now = OffsetDateTime.now();
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            DocumentMetadata record = new DocumentMetadata();
            record.setDocument(document);
            record.setFieldKey(entry.getKey());
            record.setFieldValue(serializeValue(entry.getValue()));
            record.setCreatedBy(user.userId());
            record.setCreatedAt(now);
            metadataRepository.save(record);
            createAuditLog(document, entry.getKey(), null, record.getFieldValue(), "ADDED", user, now);
        }
    }

    private void storeFileIfPresent(DocumentParent document, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String filename = document.getDocumentNumber() + "_" + Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        try {
            storageAdapter.store(filename, file.getInputStream());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store file", ex);
        }
    }

    private Map<String, Object> fetchMetadataMap(DocumentParent document) {
        return metadataRepository.findByDocument(document).stream()
                .collect(Collectors.toMap(DocumentMetadata::getFieldKey,
                        entry -> deserializeValue(entry.getFieldValue()),
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    private DocumentResponse mapToResponse(DocumentParent document, Map<String, Object> metadata) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setDocumentNumber(document.getDocumentNumber());
        response.setTitle(document.getTitle());
        response.setStatus(document.getStatus());
        response.setCreatedBy(document.getCreatedBy());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedBy(document.getUpdatedBy());
        response.setUpdatedAt(document.getUpdatedAt());
        response.setMetadata(metadata);
        return response;
    }

    private void logStatusChange(DocumentParent document, DocumentStatus oldStatus, DocumentStatus newStatus, RequestUser user,
                                 String action, String comment, OffsetDateTime when) {
        String oldPayload = serializeStatusInfo(oldStatus, null);
        String newPayload = serializeStatusInfo(newStatus, comment);
        createAuditLog(document, "status", oldPayload, newPayload, action, user, when);
    }

    private String serializeStatusInfo(DocumentStatus status, String comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status != null ? status.name() : null);
        if (comment != null && !comment.isBlank()) {
            payload.put("comment", comment);
        }
        return serializeValue(payload);
    }

    private void createAuditLog(DocumentParent document, String fieldKey, String oldValue, String newValue, String changeType, RequestUser user, OffsetDateTime when) {
        AuditLog log = new AuditLog();
        log.setDocument(document);
        log.setFieldKey(fieldKey);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setChangeType(changeType);
        log.setChangedBy(user.userId());
        log.setChangedAt(when);
        auditLogRepository.save(log);
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize metadata value", e);
        }
    }

    private Object deserializeValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            return value;
        }
    }
}
