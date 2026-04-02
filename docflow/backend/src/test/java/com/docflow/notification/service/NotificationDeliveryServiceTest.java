package com.docflow.notification.service;

import com.docflow.notification.channel.NotificationChannel;
import com.docflow.notification.domain.NotificationOutbox;
import com.docflow.notification.domain.NotificationTemplate;
import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationDeliveryStatus;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationEventType;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationMessage;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.model.NotificationRecipientType;
import com.docflow.notification.model.NotificationReferenceType;
import com.docflow.notification.render.TemplateRenderer;
import com.docflow.notification.repository.NotificationDeliveryLogRepository;
import com.docflow.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private NotificationOutboxService outboxService;

    @Mock
    private NotificationPolicyService policyService;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;

    @Mock
    private NotificationChannel notificationChannel;

    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        when(notificationChannel.getChannelType()).thenReturn(NotificationChannelType.EMAIL);
        service = new NotificationDeliveryService(
                outboxService,
                policyService,
                templateRepository,
                deliveryLogRepository,
                new TemplateRenderer(),
                List.of(new ExplicitRecipientResolver()),
                List.of(notificationChannel)
        );
    }

    @Test
    void processesOutboxAndLogsRecipientsOnSuccess() {
        NotificationOutbox outbox = new NotificationOutbox();
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationEventType.DOCUMENT_LIFECYCLE);
        event.setEventCode(NotificationEventCode.SUBMITTED_FOR_REVIEW);
        event.setReferenceType(NotificationReferenceType.DOCUMENT);
        event.setReferenceId("123");
        event.setContext(java.util.Map.of("documentId", "123"));
        NotificationExplicitRecipients recipients = new NotificationExplicitRecipients();
        recipients.setTo(List.of("maker@internal.local", "maker@internal.local"));
        recipients.setCc(List.of("reviewer@internal.local", "maker@internal.local"));
        event.setExplicitRecipients(recipients);

        NotificationPolicy policy = new NotificationPolicy();
        policy.setEnabled(true);
        policy.setChannelType(NotificationChannelType.EMAIL);
        policy.setTemplateCode("DOC_SUBMIT");
        EnumMap<NotificationRecipientType, Boolean> recipientPolicies = new EnumMap<>(NotificationRecipientType.class);
        recipientPolicies.put(NotificationRecipientType.EXPLICIT_TO, true);
        recipientPolicies.put(NotificationRecipientType.EXPLICIT_CC, true);
        policy.setRecipientPolicies(recipientPolicies);

        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateCode("DOC_SUBMIT");
        template.setSubjectTemplate("Document ${documentId}");
        template.setBodyTemplate("Document ${documentId} updated");
        template.setHtml(false);
        template.setActive(true);

        when(outboxService.markProcessing(9L)).thenReturn(Optional.of(outbox));
        when(outboxService.readPayload(outbox)).thenReturn(event);
        when(policyService.resolve(NotificationEventCode.SUBMITTED_FOR_REVIEW)).thenReturn(Optional.of(policy));
        when(templateRepository.findById("DOC_SUBMIT")).thenReturn(Optional.of(template));
        when(notificationChannel.send(any(NotificationMessage.class))).thenReturn(NotificationDispatchResult.success("SMTP"));

        service.processOutbox(9L);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationChannel).send(messageCaptor.capture());
        NotificationMessage message = messageCaptor.getValue();
        assertThat(message.getTo()).containsExactly("maker@internal.local");
        assertThat(message.getCc()).containsExactly("reviewer@internal.local");
        assertThat(message.getSubject()).isEqualTo("Document 123");
        verify(outboxService).markSent(9L);
        verify(deliveryLogRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void marksOutboxFailedWhenChannelFails() {
        NotificationOutbox outbox = new NotificationOutbox();
        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationEventType.DOCUMENT_LIFECYCLE);
        event.setEventCode(NotificationEventCode.REJECTED);
        event.setReferenceType(NotificationReferenceType.DOCUMENT);
        event.setReferenceId("456");
        NotificationExplicitRecipients recipients = new NotificationExplicitRecipients();
        recipients.setTo(List.of("maker@internal.local"));
        event.setExplicitRecipients(recipients);

        NotificationPolicy policy = new NotificationPolicy();
        policy.setEnabled(true);
        policy.setChannelType(NotificationChannelType.EMAIL);
        policy.setTemplateCode("DOC_REJECT");
        EnumMap<NotificationRecipientType, Boolean> recipientPolicies = new EnumMap<>(NotificationRecipientType.class);
        recipientPolicies.put(NotificationRecipientType.EXPLICIT_TO, true);
        policy.setRecipientPolicies(recipientPolicies);

        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateCode("DOC_REJECT");
        template.setSubjectTemplate("Rejected");
        template.setBodyTemplate("Body");
        template.setHtml(false);
        template.setActive(true);

        when(outboxService.markProcessing(10L)).thenReturn(Optional.of(outbox));
        when(outboxService.readPayload(outbox)).thenReturn(event);
        when(policyService.resolve(NotificationEventCode.REJECTED)).thenReturn(Optional.of(policy));
        when(templateRepository.findById("DOC_REJECT")).thenReturn(Optional.of(template));
        when(notificationChannel.send(any(NotificationMessage.class)))
                .thenReturn(new NotificationDispatchResult(NotificationDeliveryStatus.FAILED, "SMTP", "relay down"));

        service.processOutbox(10L);

        verify(outboxService).markFailed(10L, "relay down");
        verify(deliveryLogRepository).save(any());
    }
}
