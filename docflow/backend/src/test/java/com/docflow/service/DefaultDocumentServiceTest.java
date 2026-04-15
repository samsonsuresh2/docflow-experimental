package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.notification.service.DocumentNotificationPublisher;
import com.docflow.storage.StorageAdapter;
import com.docflow.api.dto.FilterDefinition;
import com.docflow.api.dto.FilterSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultDocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private StorageAdapter storageAdapter;
    @Mock
    private MetadataService metadataService;
    @Mock
    private AuditService auditService;
    @Mock
    private RuleService ruleService;
    @Mock
    private ConfigService configService;
    @Mock
    private RequestUserContext requestUserContext;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WorkflowPermissionService workflowPermissionService;
    @Mock
    private DocumentNotificationPublisher documentNotificationPublisher;

    @InjectMocks
    private DefaultDocumentService service;

    @Captor
    private ArgumentCaptor<String> createdByCaptor;

    private DocumentParent sampleDocument;

    @BeforeEach
    void setup() {
        sampleDocument = new DocumentParent();
        setDocumentId(sampleDocument, 1L);
        sampleDocument.setDocumentNumber("DOC-1");
        sampleDocument.setCreatedBy("maker1");
        sampleDocument.setStatus(DocumentStatus.DRAFT);
        when(metadataService.getMetadata(any())).thenReturn(Map.of());
    }

    @Test
    void makerSearchesDocumentsFilteredByCreator() {
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("maker1", Set.of("MAKER"), "MAKER")));
        when(documentRepository.searchDocuments(any(), any(), anyList(), any(), createdByCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        service.searchDocuments(null, DocumentStatus.OPEN, null, null, Map.of(), PageRequest.of(0, 10));

        assertThat(createdByCaptor.getValue()).isEqualTo("maker1");
    }

    @Test
    void reviewerSearchHasNoCreatorFilter() {
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("reviewer1", Set.of("REVIEWER"), "REVIEWER")));
        when(documentRepository.searchDocuments(any(), any(), anyList(), any(), createdByCaptor.capture()))
                .thenReturn(Page.empty());

        service.searchDocuments(null, null, null, null, Map.of(), PageRequest.of(0, 10));

        assertThat(createdByCaptor.getValue()).isNull();
    }

    @Test
    void makerCannotAccessDocumentOwnedByAnotherUser() {
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("maker1", Set.of("MAKER"), "MAKER")));
        sampleDocument.setCreatedBy("someone-else");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));

        assertThatThrownBy(() -> service.getDocument(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No documents found");
    }

    @Test
    void makerCanAccessOwnDocument() {
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("maker1", Set.of("MAKER"), "MAKER")));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));

        service.getDocument(1L);

        verify(documentRepository).findById(1L);
    }

    @Test
    void updateStatusRejectsMissingRequiredFieldsForStatus() {
        when(configService.getUploadFieldsConfig()).thenReturn("[{\"name\":\"field1\",\"requiredAtStatuses\":[\"APPROVED\"]}]");
        sampleDocument.setStatus(DocumentStatus.REVIEWED);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("approver", Set.of("APPROVER"), "APPROVER")));
        doNothing().when(workflowPermissionService).assertAllowed(eq("APPROVER"), eq(DocumentStatus.REVIEWED), eq(WorkflowActionCodes.APPROVE));

        assertThatThrownBy(() -> service.updateStatus(1L, DocumentStatus.APPROVED, new RequestUser("approver", Set.of("APPROVER"), "APPROVER"), WorkflowActionCodes.APPROVE, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing required fields");
    }

    @Test
    void updateMetadataRejectsLockedFieldChange() {
        when(configService.getUploadFieldsConfig()).thenReturn("[{\"name\":\"lockedField\",\"lockAfterFilled\":true}]");
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("maker1", Set.of("MAKER"), "MAKER")));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(metadataService.getMetadata(sampleDocument)).thenReturn(Map.of("lockedField", "initial"));

        assertThatThrownBy(() -> service.updateMetadata(1L, Map.of("lockedField", "updated"), new RequestUser("maker1", Set.of("MAKER"), "MAKER")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Locked fields");
        verify(metadataService, never()).persistMetadata(any(), any(), any());
    }

    @Test
    void updateMetadataRejectsRoleWithoutEditPermission() {
        when(configService.getUploadFieldsConfig()).thenReturn("[{\"name\":\"editable\",\"editableByRoles\":[\"REVIEWER\"]}]");
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("maker1", Set.of("MAKER"), "MAKER")));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(metadataService.getMetadata(sampleDocument)).thenReturn(Map.of());

        assertThatThrownBy(() -> service.updateMetadata(1L, Map.of("editable", "value"), new RequestUser("maker1", Set.of("MAKER"), "MAKER")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Fields not editable");
        verify(metadataService, never()).persistMetadata(any(), any(), any());
    }

    @Test
    void updateStatusRejectsForbiddenAction() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden"))
            .when(workflowPermissionService)
            .assertAllowed(eq("MAKER"), eq(DocumentStatus.DRAFT), eq(WorkflowActionCodes.SUBMIT));

        assertThatThrownBy(() -> service.updateStatus(
            1L,
            DocumentStatus.OPEN,
            new RequestUser("maker1", Set.of("MAKER"), "MAKER"),
            WorkflowActionCodes.SUBMIT,
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("cannot perform action SUBMIT");
    }

    @Test
    void reviewerApproveMovesToReviewed() {
        sampleDocument.setStatus(DocumentStatus.UNDER_REVIEW);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");
        doNothing().when(workflowPermissionService)
            .assertAllowed(eq("REVIEWER"), eq(DocumentStatus.UNDER_REVIEW), eq(WorkflowActionCodes.REVIEW_APPROVE));

        service.reviewApprove(1L, new RequestUser("reviewer1", Set.of("REVIEWER"), "REVIEWER"), "Looks good");

        assertThat(sampleDocument.getStatus()).isEqualTo(DocumentStatus.REVIEWED);
        verify(auditService).logLifecycleEvent(eq(sampleDocument), eq(DocumentStatus.UNDER_REVIEW), eq(DocumentStatus.REVIEWED),
            eq(DocumentLifecycleEventCatalog.REVIEW_APPROVED), eq("Looks good"), any(), any());
    }

    @Test
    void approverApproveMovesToApproved() {
        sampleDocument.setStatus(DocumentStatus.REVIEWED);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");
        doNothing().when(workflowPermissionService)
            .assertAllowed(eq("APPROVER"), eq(DocumentStatus.REVIEWED), eq(WorkflowActionCodes.APPROVE));

        service.approve(1L, new RequestUser("approver1", Set.of("APPROVER"), "APPROVER"), null);

        assertThat(sampleDocument.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        verify(auditService).logLifecycleEvent(eq(sampleDocument), eq(DocumentStatus.REVIEWED), eq(DocumentStatus.APPROVED),
            eq(DocumentLifecycleEventCatalog.APPROVED), isNull(), any(), any());
    }

    @Test
    void reviewerCannotFinalApproveFromUnderReview() {
        sampleDocument.setStatus(DocumentStatus.UNDER_REVIEW);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");

        assertThatThrownBy(() -> service.approve(
            1L,
            new RequestUser("reviewer1", Set.of("REVIEWER"), "REVIEWER"),
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Final approval requires REVIEWED");
    }

    @Test
    void reviewerCannotFinalApproveFromReviewed() {
        sampleDocument.setStatus(DocumentStatus.REVIEWED);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");

        assertThatThrownBy(() -> service.approve(
            1L,
            new RequestUser("reviewer1", Set.of("REVIEWER"), "REVIEWER"),
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only approvers can issue final approval");
    }

    @Test
    void approverCannotFinalApproveFromUnderReview() {
        sampleDocument.setStatus(DocumentStatus.UNDER_REVIEW);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");

        assertThatThrownBy(() -> service.approve(
            1L,
            new RequestUser("approver1", Set.of("APPROVER"), "APPROVER"),
            null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Final approval requires REVIEWED");
    }

    @Test
    void updateStatusEmitsLifecycleAuditEntry() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDocument));
        when(configService.getUploadFieldsConfig()).thenReturn("[]");
        doNothing().when(workflowPermissionService).assertAllowed(eq("MAKER"), eq(DocumentStatus.DRAFT), eq(WorkflowActionCodes.SUBMIT));

        service.updateStatus(
            1L,
            DocumentStatus.OPEN,
            new RequestUser("maker1", Set.of("MAKER"), "MAKER"),
            WorkflowActionCodes.SUBMIT,
            null
        );

        verify(auditService).logStatusChange(eq(sampleDocument), eq(DocumentStatus.DRAFT), eq(DocumentStatus.OPEN),
            eq(WorkflowActionCodes.SUBMIT), isNull(), any(), any());
        verify(auditService).logLifecycleEvent(eq(sampleDocument), eq(DocumentStatus.DRAFT), eq(DocumentStatus.OPEN),
            eq(DocumentLifecycleEventCatalog.SUBMITTED_FOR_REVIEW), isNull(), any(), any());
        verify(documentNotificationPublisher).publishLifecycleEvent(eq(sampleDocument), eq(DocumentStatus.DRAFT), eq(DocumentStatus.OPEN),
            eq(DocumentLifecycleEventCatalog.SUBMITTED_FOR_REVIEW), any(), isNull(), anyMap(), any());
    }

    @Test
    void searchDocumentsRejectsUnknownReviewFilters() {
        FilterDefinition definition = new FilterDefinition();
        definition.setKey("branch_code");
        definition.setSource(FilterSource.META_DATA);
        definition.setType("text");
        when(configService.getReviewFilterDefinitions()).thenReturn(List.of(definition));
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchDocuments(
            null,
            DocumentStatus.OPEN,
            null,
            null,
            Map.of("status", "OPEN"),
            PageRequest.of(0, 10)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported review filter");
    }

    private void setDocumentId(DocumentParent document, long id) {
        try {
            java.lang.reflect.Field field = DocumentParent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(document, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
