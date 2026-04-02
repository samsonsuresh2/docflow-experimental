package com.docflow.notification.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationEventType;
import com.docflow.notification.model.NotificationReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class DocumentNotificationPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentNotificationPublisher.class);

    private final NotificationProperties properties;
    private final NotificationPolicyService policyService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final DocumentNotificationContextBuilder contextBuilder;

    public DocumentNotificationPublisher(NotificationProperties properties,
                                         NotificationPolicyService policyService,
                                         NotificationOrchestrator notificationOrchestrator,
                                         DocumentNotificationContextBuilder contextBuilder) {
        this.properties = properties;
        this.policyService = policyService;
        this.notificationOrchestrator = notificationOrchestrator;
        this.contextBuilder = contextBuilder;
    }

    public void publishLifecycleEvent(DocumentParent document,
                                      DocumentStatus previousStatus,
                                      DocumentStatus currentStatus,
                                      String lifecycleEventCode,
                                      RequestUser actor,
                                      String comment,
                                      Map<String, Object> metadata) {
        if (!properties.isEnabled()) {
            return;
        }
        Optional<NotificationEventCode> notificationEventCode = mapEventCode(lifecycleEventCode);
        if (notificationEventCode.isEmpty()) {
            return;
        }
        if (policyService.resolve(notificationEventCode.get()).filter(policy -> policy.isEnabled() && !policy.isSendForBulk()).isEmpty()) {
            return;
        }

        try {
            NotificationEvent event = new NotificationEvent();
            event.setEventType(NotificationEventType.DOCUMENT_LIFECYCLE);
            event.setEventCode(notificationEventCode.get());
            event.setReferenceType(NotificationReferenceType.DOCUMENT);
            event.setReferenceId(String.valueOf(document.getId()));
            event.setChannelType(NotificationChannelType.EMAIL);
            event.setContext(contextBuilder.build(document, previousStatus, currentStatus, actor, comment, metadata));
            notificationOrchestrator.publish(event);
        } catch (RuntimeException ex) {
            LOGGER.warn("Unable to enqueue document notification for documentId={} lifecycleEvent={}",
                    document.getId(), lifecycleEventCode, ex);
        }
    }

    private Optional<NotificationEventCode> mapEventCode(String lifecycleEventCode) {
        if (lifecycleEventCode == null) {
            return Optional.empty();
        }
        return switch (lifecycleEventCode) {
            case "SUBMITTED_FOR_REVIEW" -> Optional.of(NotificationEventCode.SUBMITTED_FOR_REVIEW);
            case "RESUBMITTED" -> Optional.of(NotificationEventCode.RESUBMITTED);
            case "REVIEW_STARTED" -> Optional.of(NotificationEventCode.REVIEW_STARTED);
            case "SENT_BACK_TO_MAKER" -> Optional.of(NotificationEventCode.SENT_BACK_TO_MAKER);
            case "REVIEW_APPROVED" -> Optional.of(NotificationEventCode.REVIEW_APPROVED);
            case "APPROVED" -> Optional.of(NotificationEventCode.APPROVED);
            case "REJECTED" -> Optional.of(NotificationEventCode.REJECTED);
            default -> Optional.empty();
        };
    }
}
