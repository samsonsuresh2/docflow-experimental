package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentMetadata;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.repository.DocumentMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMetadataServiceTest {

    @Mock
    private DocumentMetadataRepository metadataRepository;

    @Mock
    private AuditService auditService;

    private DefaultMetadataService service;

    @BeforeEach
    void setUp() {
        service = new DefaultMetadataService(metadataRepository, auditService, new ObjectMapper());
    }

    @Test
    void persistMetadataAddsUpdatesAndRemovesFields() {
        DocumentParent document = new DocumentParent();
        ReflectionTestUtils.setField(document, "id", 10L);
        DocumentMetadata existing = metadata("status", "\"old\"");
        DocumentMetadata removed = metadata("obsolete", "\"gone\"");

        when(metadataRepository.findByDocument(document))
            .thenReturn(List.of(existing, removed))
            .thenReturn(List.of(metadata("status", "\"new\""), metadata("amount", "15")));

        Map<String, Object> result = service.persistMetadata(
            document,
            Map.of("status", "new", "amount", 15),
            new RequestUser("maker1", Set.of("MAKER"), "MAKER")
        );

        ArgumentCaptor<DocumentMetadata> saved = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(metadataRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        verify(metadataRepository).delete(removed);
        verify(auditService).logFieldUpdate(any(), org.mockito.Mockito.eq("status"), org.mockito.Mockito.eq("old"), org.mockito.Mockito.eq("new"), org.mockito.Mockito.eq("UPDATED"), any(), any());
        verify(auditService).logFieldUpdate(any(), org.mockito.Mockito.eq("amount"), org.mockito.Mockito.isNull(), org.mockito.Mockito.eq(15), org.mockito.Mockito.eq("ADDED"), any(), any());
        verify(auditService).logFieldUpdate(any(), org.mockito.Mockito.eq("obsolete"), org.mockito.Mockito.eq("gone"), org.mockito.Mockito.isNull(), org.mockito.Mockito.eq("REMOVED"), any(), any());
        assertThat(result).containsEntry("status", "new").containsEntry("amount", 15);
    }

    @Test
    void getMetadataFallsBackToRawValueWhenStoredValueIsNotJson() {
        DocumentParent document = new DocumentParent();
        when(metadataRepository.findByDocument(document)).thenReturn(List.of(metadata("legacy", "plain-text")));

        assertThat(service.getMetadata(document)).containsEntry("legacy", "plain-text");
    }

    private DocumentMetadata metadata(String key, String value) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFieldKey(key);
        metadata.setFieldValue(value);
        return metadata;
    }
}
