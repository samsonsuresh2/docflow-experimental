package com.docflow.domain.repository;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.api.dto.FilterSource;
import com.docflow.domain.DocumentMetadata;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.service.search.DocumentSearchFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DocumentRepositoryImplTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentMetadataRepository documentMetadataRepository;

    @Test
    void searchDocumentsFiltersByMetadata() {
        DocumentParent document = createDocument("DOC-100", "Loan Application", DocumentStatus.OPEN, "maker1");
        documentRepository.save(document);

        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setDocument(document);
        metadata.setFieldKey("branch_code");
        metadata.setFieldValue("BR001");
        documentMetadataRepository.save(metadata);

        DocumentSearchFilter filter = DocumentSearchFilter.metadataPlaceholder("branch_code", "BR001");
        Page<DocumentParent> results = documentRepository.searchDocuments(
            null,
            null,
            List.of(filter),
            PageRequest.of(0, 10)
        );

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getDocumentNumber()).isEqualTo("DOC-100");
    }

    @Test
    void searchDocumentsFiltersByParentField() {
        DocumentParent matching = createDocument("DOC-200", "KYC", DocumentStatus.OPEN, "maker-alpha");
        DocumentParent other = createDocument("DOC-201", "KYC", DocumentStatus.OPEN, "maker-beta");
        documentRepository.saveAll(List.of(matching, other));

        FilterDefinition definition = new FilterDefinition();
        definition.setKey("createdBy");
        definition.setSource(FilterSource.DOCUMENT_PARENT);
        definition.setType("text");

        DocumentSearchFilter filter = DocumentSearchFilter.fromDefinition(definition, "maker-alpha");
        Page<DocumentParent> results = documentRepository.searchDocuments(
            null,
            null,
            List.of(filter),
            PageRequest.of(0, 10)
        );

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getCreatedBy()).isEqualTo("maker-alpha");
    }

    private DocumentParent createDocument(String number, String title, DocumentStatus status, String createdBy) {
        DocumentParent document = new DocumentParent();
        document.setDocumentNumber(number);
        document.setTitle(title);
        document.setStatus(status);
        document.setCreatedBy(createdBy);
        document.setCreatedAt(OffsetDateTime.now());
        return document;
    }
}
