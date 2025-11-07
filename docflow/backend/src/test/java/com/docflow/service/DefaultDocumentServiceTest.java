package com.docflow.service;

import com.docflow.api.dto.DocumentSummary;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.storage.StorageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private DefaultDocumentService documentService;

    @Test
    void searchDocumentsIncludesDraftResults() {
        DocumentParent draftDocument = new DocumentParent();
        ReflectionTestUtils.setField(draftDocument, "id", 42L);
        draftDocument.setDocumentNumber("DOC-42");
        draftDocument.setTitle("Quarterly Report Draft");
        draftDocument.setStatus(DocumentStatus.DRAFT);
        draftDocument.setCreatedBy("maker1");
        draftDocument.setCreatedAt(OffsetDateTime.now());

        Pageable pageable = PageRequest.of(0, 10);
        Page<DocumentParent> repositoryResult = new PageImpl<>(List.of(draftDocument), pageable, 1);
        when(documentRepository.searchDocuments(null, DocumentStatus.DRAFT, pageable)).thenReturn(repositoryResult);

        Page<DocumentSummary> results = documentService.searchDocuments(
            null,
            DocumentStatus.DRAFT,
            null,
            null,
            pageable
        );

        verify(documentRepository).searchDocuments(null, DocumentStatus.DRAFT, pageable);
        assertThat(results.getTotalElements()).isEqualTo(1);
        DocumentSummary summary = results.getContent().get(0);
        assertThat(summary.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(summary.getDocumentNumber()).isEqualTo("DOC-42");
    }
}

