package com.docflow.service;

import com.docflow.api.dto.DocumentResponse;
import com.docflow.api.dto.DocumentUploadMetadata;
import com.docflow.context.RequestUser;
import com.docflow.domain.AuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.storage.StorageAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class DefaultDocumentService implements DocumentService {

    private final DocumentRepository documentRepository;
    private final StorageAdapter storageAdapter;
    private final MetadataService metadataService;
    private final AuditService auditService;
    private final RuleService ruleService;

    public DefaultDocumentService(DocumentRepository documentRepository,
                                  StorageAdapter storageAdapter,
                                  MetadataService metadataService,
                                  AuditService auditService,
                                  RuleService ruleService) {
        this.documentRepository = documentRepository;
        this.storageAdapter = storageAdapter;
        this.metadataService = metadataService;
        this.auditService = auditService;
        this.ruleService = ruleService;
    }

    @Override
    @Transactional
    public DocumentResponse createDocument(DocumentUploadMetadata metadata, MultipartFile file, RequestUser user) {
        validateUniqueDocumentNumber(metadata.getDocumentNumber());

        OffsetDateTime now = OffsetDateTime.now();
        DocumentParent document = new DocumentParent();
        document.setDocumentNumber(metadata.getDocumentNumber());
        document.setTitle(metadata.getTitle());
        document.setStatus(DocumentStatus.DRAFT);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(now);
        DocumentParent saved = documentRepository.save(document);

        Map<String, Object> storedMetadata = metadataService.persistMetadata(saved, metadata.getMetadata(), user);
        storeFileIfPresent(saved, file);

        return mapToResponse(saved, storedMetadata);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id) {
        DocumentParent document = requireDocument(id);
        Map<String, Object> metadata = metadataService.getMetadata(document);
        return mapToResponse(document, metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentByNumber(String documentNumber) {
        DocumentParent document = requireDocumentByNumber(documentNumber);
        Map<String, Object> metadata = metadataService.getMetadata(document);
        return mapToResponse(document, metadata);
    }

    @Override
    public DocumentResponse submitDocument(Long id, RequestUser user) {
        return updateStatus(id, DocumentStatus.OPEN, user, "SUBMIT", null);
    }

    @Override
    public DocumentResponse updateStatus(Long id, DocumentStatus status, RequestUser user, String action, String comment) {
        DocumentParent document = requireDocument(id);
        DocumentStatus previousStatus = document.getStatus();
        if (Objects.equals(previousStatus, status)) {
            Map<String, Object> metadata = metadataService.getMetadata(document);
            return mapToResponse(document, metadata);
        }

        OffsetDateTime now = OffsetDateTime.now();
        document.setStatus(status);
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        auditService.logStatusChange(document, previousStatus, status, action, comment, user, now);

        Map<String, Object> metadata = metadataService.getMetadata(document);
        return mapToResponse(document, metadata);
    }

    @Override
    public DocumentResponse updateMetadata(Long id, Map<String, Object> requestedMetadata, RequestUser user) {
        DocumentParent document = requireDocument(id);
        OffsetDateTime now = OffsetDateTime.now();
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        Map<String, Object> metadata = metadataService.persistMetadata(document, requestedMetadata, user);
        return mapToResponse(document, metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail(Long id) {
        requireDocument(id);
        return auditService.getAuditTrail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrailByDocumentNumber(String documentNumber) {
        DocumentParent document = requireDocumentByNumber(documentNumber);
        return auditService.getAuditTrail(document.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFile getDocumentFile(Long id) {
        DocumentParent document = requireDocument(id);
        String storedPath = document.getFilePath();
        if (storedPath == null || storedPath.isBlank()) {
            throw new NoSuchElementException("File not found");
        }

        return new DocumentFile(
            storageAdapter.loadAsResource(storedPath),
            Paths.get(storedPath).getFileName().toString()
        );
    }

    @Override
    public DocumentResponse approve(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.APPROVED, user, "APPROVE", comment);
    }

    @Override
    public DocumentResponse close(Long id, RequestUser user, String comment) {
        DocumentParent document = requireDocument(id);
        if (!ruleService.validateForClosure(document)) {
            throw new IllegalStateException("Document is not eligible for closure");
        }
        return updateStatus(id, DocumentStatus.CLOSED, user, "CLOSE", comment);
    }

    @Override
    public DocumentResponse moveToUnderReview(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.UNDER_REVIEW, user, "UNDER_REVIEW", comment);
    }

    @Override
    public DocumentResponse reject(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.OPEN, user, "REJECT", comment);
    }

    @Override
    public DocumentResponse rework(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.OPEN, user, "REWORK", comment);
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

    private DocumentParent requireDocumentByNumber(String documentNumber) {
        return documentRepository.findByDocumentNumber(documentNumber)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
    }

    private void storeFileIfPresent(DocumentParent document, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        String sanitizedOriginal = originalFilename.replaceAll("[\\\\/]+", "_");
        String filename = document.getDocumentNumber() + "_" + sanitizedOriginal;
        int currentYear = LocalDate.now().getYear();
        String relativePath = Paths.get(String.valueOf(currentYear), String.valueOf(document.getId()), filename)
            .toString()
            .replace('\\', '/');
        try (InputStream data = file.getInputStream()) {
            String storedPath = storageAdapter.store(relativePath, data);
            document.setFilePath(storedPath);
            documentRepository.save(document);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store file", ex);
        }
    }

    private DocumentResponse mapToResponse(DocumentParent document, Map<String, Object> metadata) {
        Map<String, Object> metadataCopy = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setDocumentNumber(document.getDocumentNumber());
        response.setTitle(document.getTitle());
        response.setStatus(document.getStatus());
        response.setCreatedBy(document.getCreatedBy());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedBy(document.getUpdatedBy());
        response.setUpdatedAt(document.getUpdatedAt());
        response.setFilePath(document.getFilePath());
        response.setMetadata(metadataCopy);
        return response;
    }
}
