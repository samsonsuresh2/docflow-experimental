package com.docflow.service;

import com.docflow.api.dto.FastTrackDecisionRequest;
import com.docflow.api.dto.FastTrackDecisionResult;
import com.docflow.api.dto.FastTrackDecisionSubmitResponse;
import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.service.DocumentLifecycleEventCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.ResourcelessTransactionManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FastTrackApprovalServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentService documentService;
    @Mock
    private AuditService auditService;

    private FastTrackApprovalService service;

    @BeforeEach
    void setup() {
        service = new FastTrackApprovalService(
            documentRepository,
            documentService,
            auditService,
            new ResourcelessTransactionManager()
        );
    }

    @Test
    void submitDecisionsRejectsIneligibleStatus() {
        DocumentParent document = new DocumentParent();
        document.setStatus(DocumentStatus.APPROVED);
        document.setCreatedBy("maker");
        document.setCreatedAt(OffsetDateTime.now().minusDays(1));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        FastTrackDecisionRequest request = new FastTrackDecisionRequest();
        request.setDocumentId("1");
        request.setDecision("A");
        RequestUser approver = new RequestUser("approver", Set.of("APPROVER"), "APPROVER");

        FastTrackDecisionSubmitResponse response = service.submitDecisions(
            List.of(request),
            approver
        );

        FastTrackDecisionResult result = response.getResults().get(0);
        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("NOT_ELIGIBLE");
        assertThat(response.getSummary().getFailed()).isEqualTo(1);
    }

    @Test
    void submitDecisionsRequiresCommentForOnHold() {
        DocumentParent document = new DocumentParent();
        document.setStatus(DocumentStatus.REVIEWED);
        document.setCreatedBy("maker");
        document.setCreatedAt(OffsetDateTime.now().minusDays(1));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(document));

        FastTrackDecisionRequest request = new FastTrackDecisionRequest();
        request.setDocumentId("2");
        request.setDecision("O");
        RequestUser approver = new RequestUser("approver", Set.of("APPROVER"), "APPROVER");

        FastTrackDecisionSubmitResponse response = service.submitDecisions(
            List.of(request),
            approver
        );

        FastTrackDecisionResult result = response.getResults().get(0);
        assertThat(result.isOk()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("COMMENT_REQUIRED");
        assertThat(response.getSummary().getFailed()).isEqualTo(1);
    }

    @Test
    void submitDecisionsUpdatesStatusAndLogsLifecycle() {
        OffsetDateTime updatedAt = OffsetDateTime.now().minusHours(2);
        DocumentParent document = new DocumentParent();
        document.setStatus(DocumentStatus.REVIEWED);
        document.setCreatedBy("maker");
        document.setCreatedAt(OffsetDateTime.now().minusDays(1));
        document.setUpdatedAt(updatedAt);
        when(documentRepository.findById(3L)).thenReturn(Optional.of(document));

        FastTrackDecisionRequest request = new FastTrackDecisionRequest();
        request.setDocumentId("3");
        request.setDecision("A");
        request.setExpectedUpdatedAt(updatedAt);
        RequestUser approver = new RequestUser("approver", Set.of("APPROVER"), "APPROVER");

        FastTrackDecisionSubmitResponse response = service.submitDecisions(
            List.of(request),
            approver
        );

        FastTrackDecisionResult result = response.getResults().get(0);
        assertThat(result.isOk()).isTrue();
        assertThat(result.getNewStatus()).isEqualTo(DocumentStatus.APPROVED.name());
        assertThat(response.getSummary().getApproved()).isEqualTo(1);
        verify(auditService).logLifecycleEvent(eq(document), eq(DocumentStatus.REVIEWED), eq(DocumentStatus.APPROVED),
            eq(DocumentLifecycleEventCatalog.FAST_TRACK_APPROVED), eq(null), eq(approver), any(OffsetDateTime.class));
    }
}
