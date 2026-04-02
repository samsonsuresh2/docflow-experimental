package com.docflow.notification.service;

import com.docflow.notification.domain.NotificationOutbox;
import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationEventType;
import com.docflow.notification.model.NotificationOutboxStatus;
import com.docflow.notification.model.NotificationReferenceType;
import com.docflow.notification.repository.NotificationOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    private NotificationOutboxService service;

    @BeforeEach
    void setUp() {
        service = new NotificationOutboxService(outboxRepository, new ObjectMapper());
    }

    @Test
    void enqueuePersistsMandatoryFieldsWithPendingDefaults() {
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationEventType.DOCUMENT_LIFECYCLE);
        event.setEventCode(NotificationEventCode.SUBMITTED_FOR_REVIEW);
        event.setReferenceType(NotificationReferenceType.DOCUMENT);
        event.setReferenceId("42");
        event.setChannelType(NotificationChannelType.EMAIL);
        event.setContext(java.util.Map.of("documentId", "42"));

        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(invocation -> {
            NotificationOutbox outbox = invocation.getArgument(0);
            java.lang.reflect.Field idField = NotificationOutbox.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(outbox, 11L);
            return outbox;
        });

        Optional<Long> id = service.enqueue(event);

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository).save(captor.capture());
        NotificationOutbox saved = captor.getValue();
        assertThat(id).contains(11L);
        assertThat(saved.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getPayloadJson()).contains("SUBMITTED_FOR_REVIEW");
    }

    @Test
    void markProcessingIncrementsAttemptCount() {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setAttemptCount(1);

        when(outboxRepository.findByIdAndStatus(5L, NotificationOutboxStatus.PENDING)).thenReturn(Optional.of(outbox));
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<NotificationOutbox> result = service.markProcessing(5L);

        assertThat(result).contains(outbox);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PROCESSING);
        assertThat(outbox.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void markFailedSetsProcessedTimestampAndTruncatesError() {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setStatus(NotificationOutboxStatus.PROCESSING);
        outbox.setAttemptCount(1);
        outbox.setCreatedAt(OffsetDateTime.now());
        String longError = "x".repeat(4500);

        when(outboxRepository.findById(7L)).thenReturn(Optional.of(outbox));
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markFailed(7L, longError);

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
        assertThat(outbox.getProcessedAt()).isNotNull();
        assertThat(outbox.getLastError()).hasSize(4000);
    }

    @Test
    void findPendingIdsReturnsOldestIdsFirst() {
        NotificationOutbox first = new NotificationOutbox();
        NotificationOutbox second = new NotificationOutbox();
        try {
            java.lang.reflect.Field idField = NotificationOutbox.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(first, 1L);
            idField.set(second, 2L);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, Pageable.ofSize(2)))
                .thenReturn(List.of(first, second));

        assertThat(service.findPendingIds(2)).containsExactly(1L, 2L);
    }
}
