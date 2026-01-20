package com.docflow.service;

import com.docflow.api.dto.FastTrackDecisionRequest;
import com.docflow.api.dto.FastTrackDecisionResult;
import com.docflow.api.dto.FastTrackDecisionSubmitResponse;
import com.docflow.api.dto.FastTrackDecisionSummary;
import com.docflow.api.dto.DocumentSummary;
import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FastTrackApprovalService {

    private static final Set<DocumentStatus> ELIGIBLE_STATUSES = Set.of(DocumentStatus.REVIEWED);
    private static final String STATUS_FILTER_KEY = "status";

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public FastTrackApprovalService(DocumentRepository documentRepository,
                                    DocumentService documentService,
                                    AuditService auditService,
                                    PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Page<DocumentSummary> searchDocuments(String documentNumber,
                                                 String metadataKey,
                                                 String metadataValue,
                                                 Map<String, Object> dynamicFilters,
                                                 Pageable pageable) {
        Map<String, Object> safeFilters = sanitizeFilters(dynamicFilters);
        return documentService.searchDocumentsByStatuses(
            documentNumber,
            ELIGIBLE_STATUSES,
            metadataKey,
            metadataValue,
            safeFilters,
            pageable
        );
    }

    private Map<String, Object> sanitizeFilters(Map<String, Object> dynamicFilters) {
        if (dynamicFilters == null || dynamicFilters.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safeFilters = new LinkedHashMap<>(dynamicFilters);
        safeFilters.entrySet().removeIf(entry ->
            entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(STATUS_FILTER_KEY)
        );
        return safeFilters;
    }

    public FastTrackDecisionSubmitResponse submitDecisions(List<FastTrackDecisionRequest> decisions, RequestUser user) {
        List<FastTrackDecisionResult> results = new ArrayList<>();
        FastTrackDecisionSummary summary = new FastTrackDecisionSummary();

        List<FastTrackDecisionRequest> requestList = decisions != null ? decisions : List.of();
        for (FastTrackDecisionRequest request : requestList) {
            DecisionType decisionType = DecisionType.from(request != null ? request.getDecision() : null);
            if (decisionType == null) {
                FastTrackDecisionResult result = new FastTrackDecisionResult();
                result.setDocumentId(request != null ? request.getDocumentId() : null);
                result.setOk(false);
                result.setErrorCode("NOT_ELIGIBLE");
                result.setMessage("Decision must be one of A, O, or R.");
                results.add(result);
                summary.setFailed(summary.getFailed() + 1);
                continue;
            }
            FastTrackDecisionResult result = processDecision(request, decisionType, user);
            results.add(result);
            if (result.isOk()) {
                switch (decisionType) {
                    case APPROVE -> summary.setApproved(summary.getApproved() + 1);
                    case ON_HOLD -> summary.setOnHold(summary.getOnHold() + 1);
                    case REJECT -> summary.setRejected(summary.getRejected() + 1);
                }
            } else {
                summary.setFailed(summary.getFailed() + 1);
            }
        }

        FastTrackDecisionSubmitResponse response = new FastTrackDecisionSubmitResponse();
        response.setSummary(summary);
        response.setResults(results);
        return response;
    }

    private FastTrackDecisionResult processDecision(FastTrackDecisionRequest request,
                                                    DecisionType decisionType,
                                                    RequestUser user) {
        return transactionTemplate.execute(status -> {
            try {
                return applyDecision(request, decisionType, user);
            } catch (FastTrackDecisionException ex) {
                status.setRollbackOnly();
                FastTrackDecisionResult result = new FastTrackDecisionResult();
                result.setDocumentId(request != null ? request.getDocumentId() : null);
                result.setOk(false);
                result.setErrorCode(ex.getCode());
                result.setMessage(ex.getMessage());
                return result;
            } catch (RuntimeException ex) {
                status.setRollbackOnly();
                FastTrackDecisionResult result = new FastTrackDecisionResult();
                result.setDocumentId(request != null ? request.getDocumentId() : null);
                result.setOk(false);
                result.setErrorCode("NOT_ELIGIBLE");
                result.setMessage("Unable to apply decision.");
                return result;
            }
        });
    }

    private FastTrackDecisionResult applyDecision(FastTrackDecisionRequest request,
                                                  DecisionType decisionType,
                                                  RequestUser user) {
        if (request == null) {
            throw new FastTrackDecisionException("NOT_FOUND", "Decision payload is missing.");
        }
        Long documentId = parseDocumentId(request.getDocumentId());
        DocumentParent document = documentRepository.findById(documentId)
            .orElseThrow(() -> new FastTrackDecisionException("NOT_FOUND", "Document not found."));

        if (!ELIGIBLE_STATUSES.contains(document.getStatus())) {
            throw new FastTrackDecisionException("NOT_ELIGIBLE", "Document is not eligible for fast-track approval.");
        }

        String trimmedComment = request.getComment() != null ? request.getComment().trim() : "";
        if (decisionType.requiresComment() && trimmedComment.isBlank()) {
            throw new FastTrackDecisionException("COMMENT_REQUIRED", "Comment is required for on-hold or reject decisions.");
        }

        OffsetDateTime expectedUpdatedAt = request.getExpectedUpdatedAt();
        if (expectedUpdatedAt != null) {
            OffsetDateTime actual = document.getUpdatedAt();
            if (actual == null || !actual.isEqual(expectedUpdatedAt)) {
                throw new FastTrackDecisionException("STALE", "Document has been updated by another user.");
            }
        }

        DocumentStatus previousStatus = document.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        document.setStatus(decisionType.targetStatus());
        document.setUpdatedBy(user.userId());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        String comment = trimmedComment.isBlank() ? null : trimmedComment;
        auditService.logStatusChange(document, previousStatus, decisionType.targetStatus(), decisionType.auditAction(), comment, user, now);
        auditService.logLifecycleEvent(document, previousStatus, decisionType.targetStatus(), decisionType.lifecycleEventCode(), comment, user, now);

        FastTrackDecisionResult result = new FastTrackDecisionResult();
        result.setDocumentId(String.valueOf(documentId));
        result.setOk(true);
        result.setNewStatus(decisionType.targetStatus().name());
        return result;
    }

    private Long parseDocumentId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new FastTrackDecisionException("NOT_FOUND", "Document id is required.");
        }
        try {
            return Long.parseLong(rawId.trim());
        } catch (NumberFormatException ex) {
            throw new FastTrackDecisionException("NOT_FOUND", "Document id is invalid.");
        }
    }

    private static class FastTrackDecisionException extends RuntimeException {

        private final String code;

        public FastTrackDecisionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private enum DecisionType {
        APPROVE("A", DocumentStatus.APPROVED, DocumentLifecycleEventCatalog.FAST_TRACK_APPROVED, "FAST_TRACK_APPROVE", false),
        ON_HOLD("O", DocumentStatus.ON_HOLD, DocumentLifecycleEventCatalog.FAST_TRACK_ON_HOLD, "FAST_TRACK_ON_HOLD", true),
        REJECT("R", DocumentStatus.REJECTED, DocumentLifecycleEventCatalog.FAST_TRACK_REJECTED, "FAST_TRACK_REJECT", true);

        private final String key;
        private final DocumentStatus targetStatus;
        private final String lifecycleEventCode;
        private final String auditAction;
        private final boolean requiresComment;

        DecisionType(String key,
                     DocumentStatus targetStatus,
                     String lifecycleEventCode,
                     String auditAction,
                     boolean requiresComment) {
            this.key = key;
            this.targetStatus = targetStatus;
            this.lifecycleEventCode = lifecycleEventCode;
            this.auditAction = auditAction;
            this.requiresComment = requiresComment;
        }

        public static DecisionType from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (DecisionType type : values()) {
                if (Objects.equals(type.key, normalized)) {
                    return type;
                }
            }
            return null;
        }

        public DocumentStatus targetStatus() {
            return targetStatus;
        }

        public String lifecycleEventCode() {
            return lifecycleEventCode;
        }

        public String auditAction() {
            return auditAction;
        }

        public boolean requiresComment() {
            return requiresComment;
        }
    }
}
