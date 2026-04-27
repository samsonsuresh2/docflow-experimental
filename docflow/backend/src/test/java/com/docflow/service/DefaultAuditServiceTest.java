package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AuditCategory;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuditServiceTest {

    @Mock
    private DocumentAuditLogRepository repository;

    private DefaultAuditService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAuditService(repository, new ObjectMapper());
    }

    @Test
    void logFieldUpdateSerializesPayloadAndDefaultsChangeType() {
        DocumentParent document = new DocumentParent();
        OffsetDateTime when = OffsetDateTime.parse("2026-04-27T10:15:30+05:30");

        service.logFieldUpdate(document, "amount", Map.of("old", 1), Map.of("new", 2), null, user(), when);

        ArgumentCaptor<DocumentAuditLog> captor = ArgumentCaptor.forClass(DocumentAuditLog.class);
        verify(repository).save(captor.capture());
        DocumentAuditLog log = captor.getValue();
        assertThat(log.getDocument()).isSameAs(document);
        assertThat(log.getAuditCategory()).isEqualTo(AuditCategory.FIELD_CHANGE);
        assertThat(log.getFieldKey()).isEqualTo("amount");
        assertThat(log.getOldValue()).contains("\"old\":1");
        assertThat(log.getNewValue()).contains("\"new\":2");
        assertThat(log.getChangeType()).isEqualTo("UPDATED");
        assertThat(log.getChangedBy()).isEqualTo("maker1");
        assertThat(log.getChangedAt()).isEqualTo(when);
    }

    @Test
    void logStatusChangeIncludesCommentOnlyWhenPresent() {
        service.logStatusChange(new DocumentParent(), DocumentStatus.OPEN, DocumentStatus.APPROVED, "APPROVE", " done ", user(), null);

        ArgumentCaptor<DocumentAuditLog> captor = ArgumentCaptor.forClass(DocumentAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFieldKey()).isEqualTo("status");
        assertThat(captor.getValue().getNewValue()).contains("APPROVED").contains("done");
    }

    @Test
    void logLifecycleEventPersistsLifecycleFields() {
        service.logLifecycleEvent(new DocumentParent(), DocumentStatus.OPEN, DocumentStatus.REJECTED, "REJECT", "bad data", user(), null);

        ArgumentCaptor<DocumentAuditLog> captor = ArgumentCaptor.forClass(DocumentAuditLog.class);
        verify(repository).save(captor.capture());
        DocumentAuditLog log = captor.getValue();
        assertThat(log.getAuditCategory()).isEqualTo(AuditCategory.LIFECYCLE);
        assertThat(log.getEventCode()).isEqualTo("REJECT");
        assertThat(log.getFromStatus()).isEqualTo("OPEN");
        assertThat(log.getToStatus()).isEqualTo("REJECTED");
        assertThat(log.getComment()).isEqualTo("bad data");
    }

    @Test
    void readMethodsDelegateToRepository() {
        DocumentAuditLog log = new DocumentAuditLog();
        when(repository.findByDocument_IdOrderByChangedAtAsc(5L)).thenReturn(List.of(log));
        when(repository.findByDocument_IdAndAuditCategoryOrderByChangedAtAsc(5L, AuditCategory.LIFECYCLE)).thenReturn(List.of(log));

        assertThat(service.getAuditTrail(5L)).containsExactly(log);
        assertThat(service.getLifecycleTimeline(5L)).containsExactly(log);
    }

    private RequestUser user() {
        return new RequestUser("maker1", Set.of("MAKER"), "MAKER");
    }
}
