package com.docflow.service;

import com.docflow.api.dto.DocumentResponse;
import com.docflow.api.dto.DocumentSummary;
import com.docflow.api.dto.DocumentUploadMetadata;
import com.docflow.api.dto.FilterDefinition;
import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.JsonConfig;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.SchemaBindingMode;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.notification.service.DocumentNotificationPublisher;
import com.docflow.service.form.FieldAccessDecision;
import com.docflow.service.form.FieldAccessEvaluator;
import com.docflow.service.form.UploadFieldConfigParser;
import com.docflow.service.form.UploadFieldDefinition;
import com.docflow.service.search.DocumentSearchFilter;
import com.docflow.storage.StorageAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional
public class DefaultDocumentService implements DocumentService {

    private final DocumentRepository documentRepository;
    private final StorageAdapter storageAdapter;
    private final MetadataService metadataService;
    private final AuditService auditService;
    private final RuleService ruleService;
    private final ConfigService configService;
    private final com.docflow.context.RequestUserContext requestUserContext;
    private final UploadFieldConfigParser uploadFieldConfigParser;
    private final WorkflowPermissionService workflowPermissionService;
    private final DocumentNotificationPublisher documentNotificationPublisher;

    public DefaultDocumentService(DocumentRepository documentRepository,
                                  StorageAdapter storageAdapter,
                                  MetadataService metadataService,
                                  AuditService auditService,
                                  RuleService ruleService,
                                  ConfigService configService,
                                  com.docflow.context.RequestUserContext requestUserContext,
                                  WorkflowPermissionService workflowPermissionService,
                                  DocumentNotificationPublisher documentNotificationPublisher,
                                  ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.storageAdapter = storageAdapter;
        this.metadataService = metadataService;
        this.auditService = auditService;
        this.ruleService = ruleService;
        this.configService = configService;
        this.requestUserContext = requestUserContext;
        this.workflowPermissionService = workflowPermissionService;
        this.documentNotificationPublisher = documentNotificationPublisher;
        this.uploadFieldConfigParser = new UploadFieldConfigParser(objectMapper);
    }

    @Override
    @Transactional
    public DocumentResponse createDocument(DocumentUploadMetadata metadata, MultipartFile file, RequestUser user) {
        OffsetDateTime now = OffsetDateTime.now();
        DocumentParent document = new DocumentParent();
        document.setDocumentNumber(generateTemporaryDocumentNumber());
        document.setTitle(metadata.getTitle());
        document.setStatus(DocumentStatus.DRAFT);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(now);
        applySchemaBinding(document);
        DocumentParent saved = documentRepository.save(document);

        saved.setDocumentNumber(generateDocumentNumber(saved.getId()));
        DocumentParent numberedDocument = documentRepository.save(saved);

        auditService.logLifecycleEvent(numberedDocument, null, DocumentStatus.DRAFT,
            DocumentLifecycleEventCatalog.DOC_UPLOADED, null, user, now);

        Map<String, Object> storedMetadata = metadataService.persistMetadata(numberedDocument, metadata.getMetadata(), user);
        storeFileIfPresent(numberedDocument, file);

        return mapToResponse(numberedDocument, storedMetadata);
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
    @Transactional(readOnly = true)
    public Page<DocumentSummary> searchDocuments(
        String documentNumber,
        DocumentStatus status,
        String metadataKey,
        String metadataValue,
        Map<String, Object> dynamicFilters,
        Pageable pageable
    ) {
        List<DocumentSearchFilter> filters = buildSearchFilters(dynamicFilters, metadataKey, metadataValue);
        String createdBy = DocumentScopeGuard.ownerConstraint(requestUserContext);
        Page<DocumentParent> documents = documentRepository.searchDocuments(
            sanitize(documentNumber),
            status,
            filters,
            pageable,
            createdBy
        );

        return documents.map(this::mapToSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentSummary> searchDocumentsByStatuses(
        String documentNumber,
        Set<DocumentStatus> statuses,
        String metadataKey,
        String metadataValue,
        Map<String, Object> dynamicFilters,
        Pageable pageable
    ) {
        List<DocumentSearchFilter> filters = buildSearchFilters(dynamicFilters, metadataKey, metadataValue);
        String createdBy = DocumentScopeGuard.ownerConstraint(requestUserContext);
        List<DocumentStatus> statusList = statuses == null ? List.of() : statuses.stream().filter(Objects::nonNull).toList();
        Page<DocumentParent> documents = documentRepository.searchDocumentsByStatuses(
            sanitize(documentNumber),
            statusList,
            filters,
            pageable,
            createdBy
        );

        return documents.map(this::mapToSummary);
    }

    @Override
    public DocumentResponse submitDocument(Long id, RequestUser user) {
        return updateStatus(id, DocumentStatus.OPEN, user, WorkflowActionCodes.SUBMIT, null);
    }

    @Override
    public DocumentResponse updateStatus(Long id, DocumentStatus status, RequestUser user, String action, String comment) {
        DocumentParent document = requireDocument(id);
        DocumentStatus previousStatus = document.getStatus();
        if (Objects.equals(previousStatus, status)) {
            Map<String, Object> metadata = metadataService.getMetadata(document);
            return mapToResponse(document, metadata);
        }
        enforceStageGuards(previousStatus, status, user);
        String resolvedAction = resolveActionCode(previousStatus, status, action);
        workflowPermissionService.assertAllowed(user.activeRole(), previousStatus, resolvedAction);

        List<UploadFieldDefinition> definitions = uploadFieldConfigParser.parse(resolveDocumentSchemaConfig(document));
        Map<String, Object> existingMetadata = metadataService.getMetadata(document);
        enforceRequiredFields(definitions, status, existingMetadata);

        OffsetDateTime now = OffsetDateTime.now();
        document.setStatus(status);
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        auditService.logStatusChange(document, previousStatus, status, resolvedAction, comment, user, now);
        auditService.logLifecycleEvent(
            document,
            previousStatus,
            status,
            resolveLifecycleEventCode(previousStatus, status),
            comment,
            user,
            now
        );

        Map<String, Object> metadata = metadataService.getMetadata(document);
        documentNotificationPublisher.publishLifecycleEvent(
            document,
            previousStatus,
            status,
            resolveLifecycleEventCode(previousStatus, status),
            user,
            comment,
            metadata
        );
        return mapToResponse(document, metadata);
    }

    @Override
    public DocumentResponse updateMetadata(Long id, Map<String, Object> requestedMetadata, RequestUser user) {
        DocumentParent document = requireDocument(id);
        List<UploadFieldDefinition> definitions = uploadFieldConfigParser.parse(resolveDocumentSchemaConfig(document));
        Map<String, Object> safeMetadata = requestedMetadata != null ? requestedMetadata : Map.of();
        Map<String, Object> existingMetadata = metadataService.getMetadata(document);
        enforceEditability(definitions, document, existingMetadata, safeMetadata);
        OffsetDateTime now = OffsetDateTime.now();
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        Map<String, Object> metadata = metadataService.persistMetadata(document, safeMetadata, user);
        return mapToResponse(document, metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAuditLog> getAuditTrail(Long id) {
        requireDocument(id);
        return auditService.getAuditTrail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAuditLog> getAuditTrailByDocumentNumber(String documentNumber) {
        DocumentParent document = requireDocumentByNumber(documentNumber);
        return auditService.getAuditTrail(document.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAuditLog> getLifecycleTimeline(Long id) {
        requireDocument(id);
        return auditService.getLifecycleTimeline(id);
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
        return updateStatus(id, DocumentStatus.APPROVED, user, WorkflowActionCodes.APPROVE, comment);
    }

    @Override
    public DocumentResponse reviewApprove(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.REVIEWED, user, WorkflowActionCodes.REVIEW_APPROVE, comment);
    }

    @Override
    public DocumentResponse close(Long id, RequestUser user, String comment) {
        DocumentParent document = requireDocument(id);
        if (!ruleService.validateForClosure(document)) {
            throw new IllegalStateException("Document is not eligible for closure");
        }
        return updateStatus(id, DocumentStatus.CLOSED, user, WorkflowActionCodes.CLOSE, comment);
    }

    @Override
    public DocumentResponse moveToUnderReview(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.UNDER_REVIEW, user, WorkflowActionCodes.START_REVIEW, comment);
    }

    @Override
    public DocumentResponse reject(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.REJECTED, user, WorkflowActionCodes.REJECT, comment);
    }

    @Override
    public DocumentResponse rework(Long id, RequestUser user, String comment) {
        return updateStatus(id, DocumentStatus.REWORK, user, WorkflowActionCodes.REWORK, comment);
    }

    private void applySchemaBinding(DocumentParent document) {
        JsonConfig schema = configService.resolveUploadSchemaForNewDocument();
        Integer schemaVersion = schema.getSchemaVersion();
        if (schemaVersion == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Schema version is missing");
        }
        if (schemaVersion == 0) {
            document.setSchemaBindingMode(SchemaBindingMode.FLOATING_SANDBOX);
            document.setSchemaVersion(0);
        } else {
            document.setSchemaBindingMode(SchemaBindingMode.FIXED_VERSION);
            document.setSchemaVersion(schemaVersion);
        }
    }

    private String resolveDocumentSchemaConfig(DocumentParent document) {
        JsonConfig binding = new JsonConfig();
        binding.setSchemaVersion(document.getSchemaVersion());
        binding.setSchemaStatus((document.getSchemaBindingMode() == SchemaBindingMode.FLOATING_SANDBOX || Integer.valueOf(0).equals(document.getSchemaVersion()))
            ? com.docflow.domain.SchemaStatus.SANDBOX
            : com.docflow.domain.SchemaStatus.ACTIVE);
        String config = configService.getUploadFieldsConfigForBinding(binding);
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                "Schema configuration not found for document binding");
        }
        return config;
    }

    private void enforceRequiredFields(List<UploadFieldDefinition> definitions,
                                       DocumentStatus targetStatus,
                                       Map<String, Object> metadata) {
        if (definitions.isEmpty()) {
            return;
        }
        String activeRole = requestUserContext.getCurrentUser()
            .map(RequestUser::activeRole)
            .orElse(null);
        List<String> missing = new ArrayList<>();
        for (UploadFieldDefinition definition : definitions) {
            boolean visibleIfPasses = FieldAccessEvaluator.evaluateVisibleIf(definition, metadata);
            Object currentValue = metadata != null ? metadata.get(definition.getName()) : null;
            FieldAccessDecision access = FieldAccessEvaluator.evaluate(
                definition,
                activeRole,
                targetStatus,
                currentValue,
                visibleIfPasses
            );
            if (access.isRequiredNow() && !FieldAccessEvaluator.isValueFilled(currentValue)) {
                missing.add(definition.getDisplayLabel());
            }
        }
        if (!missing.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Missing required fields: " + String.join(", ", missing)
            );
        }
    }

    private void enforceEditability(List<UploadFieldDefinition> definitions,
                                    DocumentParent document,
                                    Map<String, Object> existingMetadata,
                                    Map<String, Object> requestedMetadata) {
        if (definitions.isEmpty()) {
            return;
        }
        String activeRole = requestUserContext.getCurrentUser()
            .map(RequestUser::activeRole)
            .orElse(null);

        Map<String, Object> combinedMetadata = new LinkedHashMap<>();
        if (existingMetadata != null) {
            combinedMetadata.putAll(existingMetadata);
        }
        if (requestedMetadata != null) {
            combinedMetadata.putAll(requestedMetadata);
        }

        List<String> editViolations = new ArrayList<>();
        List<String> lockViolations = new ArrayList<>();

        for (UploadFieldDefinition definition : definitions) {
            Object currentValue = existingMetadata != null ? existingMetadata.get(definition.getName()) : null;
            Object requestedValue = requestedMetadata != null ? requestedMetadata.get(definition.getName()) : null;
            boolean visibleIfPasses = FieldAccessEvaluator.evaluateVisibleIf(definition, combinedMetadata);
            FieldAccessDecision access = FieldAccessEvaluator.evaluate(
                definition,
                activeRole,
                document.getStatus(),
                currentValue,
                visibleIfPasses
            );

            boolean fieldPresentInRequest = requestedMetadata != null && requestedMetadata.containsKey(definition.getName());
            if (fieldPresentInRequest && !access.isEditable()) {
                editViolations.add(definition.getDisplayLabel());
            }

            if (definition.isLockAfterFilled() && FieldAccessEvaluator.isValueFilled(currentValue)) {
                if (!fieldPresentInRequest) {
                    lockViolations.add(definition.getDisplayLabel());
                } else if (!valuesAreEqual(currentValue, requestedValue)) {
                    lockViolations.add(definition.getDisplayLabel());
                }
            }
        }

        if (!editViolations.isEmpty() || !lockViolations.isEmpty()) {
            List<String> messages = new ArrayList<>();
            if (!editViolations.isEmpty()) {
                messages.add("Fields not editable: " + String.join(", ", editViolations));
            }
            if (!lockViolations.isEmpty()) {
                messages.add("Locked fields cannot be changed: " + String.join(", ", lockViolations));
            }
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                String.join(" | ", messages)
            );
        }
    }

    private boolean valuesAreEqual(Object existingValue, Object requestedValue) {
        if (existingValue == null && requestedValue == null) {
            return true;
        }
        if (existingValue == null || requestedValue == null) {
            return false;
        }
        if (existingValue instanceof Number && requestedValue instanceof Number) {
            return Double.compare(((Number) existingValue).doubleValue(), ((Number) requestedValue).doubleValue()) == 0;
        }
        if (existingValue instanceof Iterable<?> existingIterable && requestedValue instanceof Iterable<?> requestedIterable) {
            java.util.Iterator<?> a = existingIterable.iterator();
            java.util.Iterator<?> b = requestedIterable.iterator();
            while (a.hasNext() && b.hasNext()) {
                Object nextA = a.next();
                Object nextB = b.next();
                if (!valuesAreEqual(nextA, nextB)) {
                    return false;
                }
            }
            return !a.hasNext() && !b.hasNext();
        }
        return existingValue.toString().equals(requestedValue.toString());
    }

    private DocumentParent requireDocument(Long id) {
        DocumentParent document = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
        DocumentScopeGuard.assertCanAccess(document, requestUserContext);
        return document;
    }

    private DocumentParent requireDocumentByNumber(String documentNumber) {
        DocumentParent document = documentRepository.findByDocumentNumber(documentNumber)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
        DocumentScopeGuard.assertCanAccess(document, requestUserContext);
        return document;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        response.setAllowedActions(resolveAllowedActions(document));
        response.setId(document.getId());
        response.setDocumentNumber(document.getDocumentNumber());
        response.setTitle(document.getTitle());
        response.setStatus(document.getStatus());
        response.setCreatedBy(document.getCreatedBy());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedBy(document.getUpdatedBy());
        response.setUpdatedAt(document.getUpdatedAt());
        response.setFilePath(document.getFilePath());
        response.setSchemaBindingMode(document.getSchemaBindingMode());
        response.setSchemaVersion(document.getSchemaVersion());
        response.setMetadata(metadataCopy);
        return response;
    }

    private String resolveActionCode(DocumentStatus previousStatus, DocumentStatus targetStatus, String action) {
        if (!"STATUS_UPDATE".equalsIgnoreCase(action) || previousStatus == null || targetStatus == null) {
            return action;
        }
        return switch (targetStatus) {
            case OPEN -> WorkflowActionCodes.SUBMIT;
            case UNDER_REVIEW -> WorkflowActionCodes.START_REVIEW;
            case REVIEWED -> WorkflowActionCodes.REVIEW_APPROVE;
            case APPROVED -> WorkflowActionCodes.APPROVE;
            case REJECTED -> WorkflowActionCodes.REJECT;
            case REWORK -> WorkflowActionCodes.REWORK;
            case CLOSED -> WorkflowActionCodes.CLOSE;
            default -> action;
        };
    }

    private String resolveLifecycleEventCode(DocumentStatus previousStatus, DocumentStatus targetStatus) {
        if (previousStatus == null || targetStatus == null) {
            return DocumentLifecycleEventCatalog.STATUS_CHANGED;
        }
        if (previousStatus == DocumentStatus.DRAFT && targetStatus == DocumentStatus.OPEN) {
            return DocumentLifecycleEventCatalog.SUBMITTED_FOR_REVIEW;
        }
        if (previousStatus == DocumentStatus.REWORK && targetStatus == DocumentStatus.OPEN) {
            return DocumentLifecycleEventCatalog.RESUBMITTED;
        }
        if (previousStatus == DocumentStatus.OPEN && targetStatus == DocumentStatus.UNDER_REVIEW) {
            return DocumentLifecycleEventCatalog.REVIEW_STARTED;
        }
        if (previousStatus == DocumentStatus.UNDER_REVIEW && targetStatus == DocumentStatus.REWORK) {
            return DocumentLifecycleEventCatalog.SENT_BACK_TO_MAKER;
        }
        if (previousStatus == DocumentStatus.UNDER_REVIEW && targetStatus == DocumentStatus.REVIEWED) {
            return DocumentLifecycleEventCatalog.REVIEW_APPROVED;
        }
        if (previousStatus == DocumentStatus.REVIEWED && targetStatus == DocumentStatus.APPROVED) {
            return DocumentLifecycleEventCatalog.APPROVED;
        }
        if (previousStatus == DocumentStatus.UNDER_REVIEW && targetStatus == DocumentStatus.REJECTED) {
            return DocumentLifecycleEventCatalog.REJECTED;
        }
        if (previousStatus == DocumentStatus.REVIEWED && targetStatus == DocumentStatus.REJECTED) {
            return DocumentLifecycleEventCatalog.REJECTED;
        }
        if ((previousStatus == DocumentStatus.APPROVED || previousStatus == DocumentStatus.REJECTED)
            && targetStatus == DocumentStatus.CLOSED) {
            return DocumentLifecycleEventCatalog.REVIEW_COMPLETED;
        }
        return DocumentLifecycleEventCatalog.STATUS_CHANGED;
    }

    private void enforceStageGuards(DocumentStatus previousStatus, DocumentStatus targetStatus, RequestUser user) {
        if (previousStatus == null || targetStatus == null || user == null) {
            return;
        }
        String activeRole = user.activeRole();
        if (targetStatus == DocumentStatus.REVIEWED) {
            if (previousStatus != DocumentStatus.UNDER_REVIEW) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Review approval requires UNDER_REVIEW status.");
            }
            if (!"REVIEWER".equalsIgnoreCase(activeRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reviewers can approve review decisions.");
            }
        }
        if (targetStatus == DocumentStatus.APPROVED) {
            if (previousStatus != DocumentStatus.REVIEWED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Final approval requires REVIEWED status.");
            }
            if (!"APPROVER".equalsIgnoreCase(activeRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approvers can issue final approval.");
            }
        }
        if (targetStatus == DocumentStatus.REJECTED) {
            if (previousStatus == DocumentStatus.UNDER_REVIEW && !"REVIEWER".equalsIgnoreCase(activeRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reviewers can reject during review.");
            }
            if (previousStatus == DocumentStatus.REVIEWED && !"APPROVER".equalsIgnoreCase(activeRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approvers can reject after review.");
            }
        }
        if (targetStatus == DocumentStatus.REWORK) {
            if (previousStatus == DocumentStatus.UNDER_REVIEW && !"REVIEWER".equalsIgnoreCase(activeRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reviewers can request rework.");
            }
            if (previousStatus == DocumentStatus.REVIEWED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rework is not allowed after review completion.");
            }
        }
        if (targetStatus == DocumentStatus.UNDER_REVIEW && !"REVIEWER".equalsIgnoreCase(activeRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reviewers can start review.");
        }
    }

    private List<String> resolveAllowedActions(DocumentParent document) {
        DocumentStatus status = document != null ? document.getStatus() : null;
        String role = requestUserContext.getCurrentUser().map(RequestUser::activeRole).orElse(null);
        Set<String> allowed = workflowPermissionService.getAllowedActions(role, status);
        return allowed.stream().sorted().toList();
    }

    private List<DocumentSearchFilter> buildSearchFilters(Map<String, Object> dynamicFilters,
                                                          String metadataKey,
                                                          String metadataValue) {
        Map<String, Object> submittedFilters = dynamicFilters != null ? dynamicFilters : Map.of();
        List<FilterDefinition> definitions = configService.getReviewFilterDefinitions();
        if (!submittedFilters.isEmpty()) {
            Set<String> allowedKeys = definitions.stream()
                .map(FilterDefinition::getKey)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
            List<String> unknownKeys = submittedFilters.keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .filter(key -> !allowedKeys.contains(key))
                .sorted()
                .toList();
            if (!unknownKeys.isEmpty()) {
                String allowedList = allowedKeys.isEmpty() ? "(none)" : String.join(", ", allowedKeys);
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported review filter(s): " + String.join(", ", unknownKeys)
                        + ". Allowed filters: " + allowedList
                );
            }
        }
        Map<String, Object> normalizedFilters = new LinkedHashMap<>(submittedFilters);

        List<DocumentSearchFilter> filters = new ArrayList<>();
        for (FilterDefinition definition : definitions) {
            Object candidate = normalizedFilters.get(definition.getKey());
            DocumentSearchFilter filter = DocumentSearchFilter.fromDefinition(definition, candidate);
            if (filter != null) {
                filters.add(filter);
            }
        }

        String sanitizedMetadataKey = sanitize(metadataKey);
        String sanitizedMetadataValue = sanitize(metadataValue);
        if (sanitizedMetadataKey != null && sanitizedMetadataValue != null) {
            DocumentSearchFilter placeholder = DocumentSearchFilter.metadataPlaceholder(
                sanitizedMetadataKey,
                sanitizedMetadataValue
            );
            if (placeholder != null) {
                filters.add(placeholder);
            }
        }

        return filters;
    }

    private DocumentSummary mapToSummary(DocumentParent document) {
        DocumentSummary summary = new DocumentSummary();
        summary.setAllowedActions(resolveAllowedActions(document));
        summary.setId(document.getId());
        summary.setDocumentNumber(document.getDocumentNumber());
        summary.setTitle(document.getTitle());
        summary.setStatus(document.getStatus());
        summary.setCreatedBy(document.getCreatedBy());
        summary.setCreatedAt(document.getCreatedAt());
        summary.setUpdatedBy(document.getUpdatedBy());
        summary.setUpdatedAt(document.getUpdatedAt());
        return summary;
    }

    private String generateTemporaryDocumentNumber() {
        return "TMP-" + System.nanoTime();
    }

    private String generateDocumentNumber(Long id) {
        return String.valueOf(id);
    }
}
