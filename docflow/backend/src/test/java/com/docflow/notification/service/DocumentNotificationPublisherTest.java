package com.docflow.notification.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentNotificationPublisherTest {

    @Test
    void publishesSupportedLifecycleEventsToOutbox() {
        NotificationProperties properties = new NotificationProperties();
        NotificationPolicyService policyService = mock(NotificationPolicyService.class);
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        DocumentNotificationContextBuilder contextBuilder = mock(DocumentNotificationContextBuilder.class);
        DocumentNotificationPublisher publisher = new DocumentNotificationPublisher(properties, policyService, orchestrator, contextBuilder);

        NotificationPolicy policy = new NotificationPolicy();
        policy.setEnabled(true);
        policy.setSendForBulk(false);
        when(policyService.resolve(NotificationEventCode.APPROVED)).thenReturn(Optional.of(policy));
        when(contextBuilder.build(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("documentId", "42"));
        when(orchestrator.publish(org.mockito.ArgumentMatchers.any(NotificationEvent.class))).thenReturn(Optional.of(1L));

        DocumentParent document = new DocumentParent();
        setDocumentId(document, 42L);

        publisher.publishLifecycleEvent(
                document,
                DocumentStatus.REVIEWED,
                DocumentStatus.APPROVED,
                "APPROVED",
                new RequestUser("approver1", Set.of("APPROVER"), "APPROVER"),
                "approved",
                Map.of(),
                OffsetDateTime.parse("2026-04-13T10:00:00Z")
        );

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(orchestrator).publish(captor.capture());
        assertThat(captor.getValue().getEventCode()).isEqualTo(NotificationEventCode.APPROVED);
        assertThat(captor.getValue().getReferenceId()).isEqualTo("42");
    }

    @Test
    void skipsUnsupportedOrDisabledEvents() {
        NotificationProperties properties = new NotificationProperties();
        NotificationPolicyService policyService = mock(NotificationPolicyService.class);
        NotificationOrchestrator orchestrator = mock(NotificationOrchestrator.class);
        DocumentNotificationContextBuilder contextBuilder = mock(DocumentNotificationContextBuilder.class);
        DocumentNotificationPublisher publisher = new DocumentNotificationPublisher(properties, policyService, orchestrator, contextBuilder);

        NotificationPolicy disabled = new NotificationPolicy();
        disabled.setEnabled(false);
        when(policyService.resolve(NotificationEventCode.REJECTED)).thenReturn(Optional.of(disabled));

        DocumentParent document = new DocumentParent();
        setDocumentId(document, 42L);
        publisher.publishLifecycleEvent(
                document,
                DocumentStatus.UNDER_REVIEW,
                DocumentStatus.REJECTED,
                "REJECTED",
                new RequestUser("reviewer1", Set.of("REVIEWER"), "REVIEWER"),
                "rejected",
                Map.of(),
                OffsetDateTime.parse("2026-04-13T10:00:00Z")
        );
        publisher.publishLifecycleEvent(
                document,
                DocumentStatus.APPROVED,
                DocumentStatus.CLOSED,
                "REVIEW_COMPLETED",
                new RequestUser("approver1", Set.of("APPROVER"), "APPROVER"),
                null,
                Map.of(),
                OffsetDateTime.parse("2026-04-13T10:05:00Z")
        );

        verify(orchestrator, never()).publish(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
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
