package com.docflow.web;

import com.docflow.api.dto.DocumentActionRequest;
import com.docflow.api.dto.DocumentResponse;
import com.docflow.api.dto.DocumentSummary;
import com.docflow.api.dto.RelatedEntityResponse;
import com.docflow.api.dto.UpdateMetadataRequest;
import com.docflow.api.dto.UpdateStatusRequest;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.DocumentStatus;
import com.docflow.service.DocumentFile;
import com.docflow.service.DocumentService;
import com.docflow.service.RelatedEntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerDirectTest {

    private DocumentService documentService;
    private RelatedEntityService relatedEntityService;
    private RequestUserContext requestUserContext;
    private DocumentController controller;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        relatedEntityService = mock(RelatedEntityService.class);
        requestUserContext = mock(RequestUserContext.class);
        controller = new DocumentController(documentService, relatedEntityService, requestUserContext, new ObjectMapper());
        when(requestUserContext.requireUser()).thenReturn(user());
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(user()));
    }

    @Test
    void searchDocumentsParsesSortFiltersAndMakerEmptyMessage() {
        when(documentService.searchDocuments(any(), any(), any(), any(), anyMap(), any(Pageable.class)))
            .thenReturn(new PageImpl<DocumentSummary>(List.of()));

        var response = controller.searchDocuments(
            "approved",
            "DOC-1",
            "region",
            "west",
            0,
            5,
            "documentNumber",
            "desc",
            "{\"amount\":{\"gte\":10}}"
        );

        assertThat(response.getHeaders().getFirst("X-Docflow-Message")).isNotBlank();
        verify(documentService).searchDocuments(
            eq("DOC-1"),
            eq(DocumentStatus.APPROVED),
            eq("region"),
            eq("west"),
            eq(Map.of("amount", Map.of("gte", 10))),
            any(Pageable.class)
        );
    }

    @Test
    void searchDocumentsRejectsInvalidStatusAndFilters() {
        assertThatThrownBy(() -> controller.searchDocuments("missing", null, null, null, 0, 10, "x", "x", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> controller.searchDocuments(null, null, null, null, 0, 10, "x", "x", "{bad"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void downloadFileBuildsAttachmentResponseAndMapsMissingFileToNotFound() {
        when(documentService.getDocumentFile(10L)).thenReturn(new DocumentFile(new ByteArrayResource("data".getBytes()), "loan.pdf"));
        when(documentService.getDocumentFile(99L)).thenThrow(new NoSuchElementException("missing"));

        var found = controller.downloadFile(10L);
        var missing = controller.downloadFile(99L);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getHeaders().getFirst("Content-Disposition")).contains("loan.pdf");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void auditAndTimelineResponsesMapJsonAndFallbackValues() {
        DocumentAuditLog audit = auditLog();
        audit.setOldValue("{\"amount\":1}");
        audit.setNewValue("not-json");
        when(documentService.getAuditTrail(10L)).thenReturn(List.of(audit));
        when(documentService.getAuditTrailByDocumentNumber("DOC-1")).thenReturn(List.of(audit));
        when(documentService.getLifecycleTimeline(10L)).thenReturn(List.of(audit));

        assertThat(controller.getAuditTrail(10L).getBody()).hasSize(1);
        assertThat(controller.getAuditTrailByNumber("DOC-1").getBody()).hasSize(1);
        assertThat(controller.getTimeline(10L).getBody().get(0).getEventLabel()).isNotBlank();
    }

    @Test
    void relatedEntityChecksDocumentBeforeDelegating() {
        RelatedEntityResponse related = new RelatedEntityResponse();
        when(relatedEntityService.getRelatedEntity(10L, "LOAN_DATA")).thenReturn(related);

        assertThat(controller.getRelatedEntity(10L, "LOAN_DATA").getBody()).isSameAs(related);
        verify(documentService).getDocument(10L);
    }

    @Test
    void workflowEndpointsDelegateWithCurrentUserAndOptionalComment() {
        DocumentActionRequest request = new DocumentActionRequest();
        request.setComment("done");
        DocumentResponse response = new DocumentResponse();
        when(documentService.submitDocument(10L, user())).thenReturn(response);
        when(documentService.reviewApprove(10L, user(), "done")).thenReturn(response);
        when(documentService.approve(10L, user(), null)).thenReturn(response);
        when(documentService.reject(10L, user(), "done")).thenReturn(response);
        when(documentService.rework(10L, user(), "done")).thenReturn(response);
        when(documentService.close(10L, user(), "done")).thenReturn(response);
        when(documentService.moveToUnderReview(10L, user(), "done")).thenReturn(response);

        assertThat(controller.submitDocument(10L).getBody()).isSameAs(response);
        assertThat(controller.reviewApprove(10L, request).getBody()).isSameAs(response);
        assertThat(controller.approve(10L, null).getBody()).isSameAs(response);
        assertThat(controller.reject(10L, request).getBody()).isSameAs(response);
        assertThat(controller.rework(10L, request).getBody()).isSameAs(response);
        assertThat(controller.close(10L, request).getBody()).isSameAs(response);
        assertThat(controller.moveToUnderReview(10L, request).getBody()).isSameAs(response);
    }

    @Test
    void statusAndMetadataUpdatesDelegateWithRequestBodyValues() {
        DocumentResponse response = new DocumentResponse();
        UpdateStatusRequest statusRequest = new UpdateStatusRequest();
        statusRequest.setStatus(DocumentStatus.REJECTED);
        statusRequest.setComment("bad");
        UpdateMetadataRequest metadataRequest = new UpdateMetadataRequest();
        metadataRequest.setMetadata(Map.of("amount", 10));
        when(documentService.updateStatus(10L, DocumentStatus.REJECTED, user(), "STATUS_UPDATE", "bad")).thenReturn(response);
        when(documentService.updateMetadata(10L, Map.of("amount", 10), user())).thenReturn(response);

        assertThat(controller.updateStatus(10L, statusRequest).getBody()).isSameAs(response);
        assertThat(controller.updateMetadata(10L, metadataRequest).getBody()).isSameAs(response);
    }

    private DocumentAuditLog auditLog() {
        DocumentAuditLog log = new DocumentAuditLog();
        log.setFieldKey("amount");
        log.setChangeType("UPDATED");
        log.setChangedBy("maker1");
        log.setChangedAt(OffsetDateTime.parse("2026-04-27T10:15:30+05:30"));
        log.setEventCode("APPROVED");
        log.setFromStatus("OPEN");
        log.setToStatus("APPROVED");
        log.setComment("ok");
        return log;
    }

    private RequestUser user() {
        return new RequestUser("maker1", Set.of("MAKER"), "MAKER");
    }
}
