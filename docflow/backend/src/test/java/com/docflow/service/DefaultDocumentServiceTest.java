package com.docflow.service;

import com.docflow.api.dto.DocumentSummary;
import com.docflow.api.dto.FilterDefinition;
import com.docflow.api.dto.FilterSource;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.service.search.DocumentSearchFilter;
import com.docflow.storage.StorageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

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
        when(configService.getReviewFilterDefinitions()).thenReturn(Collections.emptyList());
        when(documentRepository.searchDocuments(null, DocumentStatus.DRAFT, Collections.emptyList(), pageable))
            .thenReturn(repositoryResult);

        Page<DocumentSummary> results = documentService.searchDocuments(
            null,
            DocumentStatus.DRAFT,
            null,
            null,
            Collections.emptyMap(),
            pageable
        );

        verify(documentRepository).searchDocuments(null, DocumentStatus.DRAFT, Collections.emptyList(), pageable);
        assertThat(results.getTotalElements()).isEqualTo(1);
        DocumentSummary summary = results.getContent().get(0);
        assertThat(summary.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(summary.getDocumentNumber()).isEqualTo("DOC-42");
    }

    @Test
    void searchDocumentsAppliesDynamicFiltersFromConfig() {
        FilterDefinition branchFilter = new FilterDefinition();
        branchFilter.setKey("branch_code");
        branchFilter.setSource(FilterSource.META_DATA);
        branchFilter.setType("dropdown");

        Pageable pageable = PageRequest.of(0, 10);
        when(configService.getReviewFilterDefinitions()).thenReturn(List.of(branchFilter));
        when(documentRepository.searchDocuments(any(), any(), any(), any()))
            .thenReturn(Page.empty(pageable));

        documentService.searchDocuments(
            null,
            null,
            null,
            null,
            Map.of("branch_code", "BR001"),
            pageable
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentSearchFilter>> filtersCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).searchDocuments(
            null,
            null,
            filtersCaptor.capture(),
            pageable
        );

        List<DocumentSearchFilter> appliedFilters = filtersCaptor.getValue();
        assertThat(appliedFilters).hasSize(1);
        DocumentSearchFilter filter = appliedFilters.get(0);
        assertThat(filter.getKey()).isEqualTo("branch_code");
        assertThat(filter.getSource()).isEqualTo(FilterSource.META_DATA);
        assertThat(filter.getRawValue()).isEqualTo("BR001");
    }
}

