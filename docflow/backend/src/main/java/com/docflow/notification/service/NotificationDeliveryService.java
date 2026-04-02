package com.docflow.notification.service;

import com.docflow.notification.channel.NotificationChannel;
import com.docflow.notification.domain.NotificationDeliveryLog;
import com.docflow.notification.domain.NotificationOutbox;
import com.docflow.notification.domain.NotificationTemplate;
import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationMessage;
import com.docflow.notification.model.NotificationOutboxStatus;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.render.TemplateRenderer;
import com.docflow.notification.repository.NotificationDeliveryLogRepository;
import com.docflow.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationOutboxService outboxService;
    private final NotificationPolicyService policyService;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final TemplateRenderer templateRenderer;
    private final List<RecipientResolver> recipientResolvers;
    private final Map<NotificationChannelType, NotificationChannel> channelsByType;

    public NotificationDeliveryService(NotificationOutboxService outboxService,
                                       NotificationPolicyService policyService,
                                       NotificationTemplateRepository templateRepository,
                                       NotificationDeliveryLogRepository deliveryLogRepository,
                                       TemplateRenderer templateRenderer,
                                       List<RecipientResolver> recipientResolvers,
                                       List<NotificationChannel> channels) {
        this.outboxService = outboxService;
        this.policyService = policyService;
        this.templateRepository = templateRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.templateRenderer = templateRenderer;
        this.recipientResolvers = recipientResolvers;
        this.channelsByType = new HashMap<>();
        for (NotificationChannel channel : channels) {
            this.channelsByType.put(channel.getChannelType(), channel);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOutbox(Long outboxId) {
        Optional<NotificationOutbox> outboxOptional = outboxService.markProcessing(outboxId);
        if (outboxOptional.isEmpty()) {
            return;
        }

        NotificationOutbox outbox = outboxOptional.get();
        if (outbox.getStatus() != NotificationOutboxStatus.PROCESSING) {
            return;
        }

        try {
            NotificationEvent event = outboxService.readPayload(outbox);
            NotificationPolicy policy = policyService.resolve(event.getEventCode())
                    .orElseThrow(() -> new IllegalStateException("Notification event config missing for " + event.getEventCode()));
            if (!policy.isEnabled()) {
                throw new IllegalStateException("Notification event disabled for " + event.getEventCode());
            }

            NotificationExplicitRecipients recipients = resolveRecipients(event, policy);
            NotificationMessage message = buildMessage(event, policy, recipients);
            NotificationChannel channel = channelsByType.get(message.getChannelType());
            if (channel == null) {
                throw new IllegalStateException("Notification channel not configured: " + message.getChannelType());
            }

            NotificationDispatchResult result = channel.send(message);
            logDelivery(event, message, result);
            if (result.status() == com.docflow.notification.model.NotificationDeliveryStatus.SUCCESS) {
                outboxService.markSent(outboxId);
            } else {
                outboxService.markFailed(outboxId, result.errorMessage());
            }
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to process notification outbox {}", outboxId, ex);
            outboxService.markFailed(outboxId, ex.getMessage());
        } finally {
            cleanupAttachment(outbox);
        }
    }

    private NotificationExplicitRecipients resolveRecipients(NotificationEvent event, NotificationPolicy policy) {
        for (RecipientResolver recipientResolver : recipientResolvers) {
            if (recipientResolver.supports(event.getReferenceType())) {
                return deduplicate(recipientResolver.resolve(event, policy));
            }
        }
        return deduplicate(event.getExplicitRecipients());
    }

    private NotificationMessage buildMessage(NotificationEvent event,
                                             NotificationPolicy policy,
                                             NotificationExplicitRecipients recipients) {
        NotificationContentSource contentSource = resolveContent(event, policy);
        NotificationMessage message = new NotificationMessage();
        message.setChannelType(event.getChannelType() != null ? event.getChannelType() : policy.getChannelType());
        message.setTo(recipients.getTo());
        message.setCc(recipients.getCc());
        message.setSubject(templateRenderer.render(contentSource.subjectTemplate(), event.getContext()));
        message.setBody(templateRenderer.render(contentSource.bodyTemplate(), event.getContext()));
        message.setHtml(contentSource.html());
        message.setAttachment(event.getAttachment());
        return message;
    }

    private NotificationContentSource resolveContent(NotificationEvent event, NotificationPolicy policy) {
        if (event.getContentOverride() != null) {
            return new NotificationContentSource(
                    event.getContentOverride().getSubjectTemplate(),
                    event.getContentOverride().getBodyTemplate(),
                    event.getContentOverride().isHtml()
            );
        }
        String templateCode = event.getTemplateCode() != null ? event.getTemplateCode() : policy.getTemplateCode();
        NotificationTemplate template = templateRepository.findById(templateCode)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(() -> new IllegalStateException("Notification template missing: " + templateCode));
        return new NotificationContentSource(template.getSubjectTemplate(), template.getBodyTemplate(), Boolean.TRUE.equals(template.getHtml()));
    }

    private NotificationExplicitRecipients deduplicate(NotificationExplicitRecipients recipients) {
        NotificationExplicitRecipients safeRecipients = recipients != null ? recipients : new NotificationExplicitRecipients();
        LinkedHashSet<String> to = new LinkedHashSet<>(safeRecipients.getTo());
        LinkedHashSet<String> cc = new LinkedHashSet<>();
        for (String address : safeRecipients.getCc()) {
            if (!to.contains(address)) {
                cc.add(address);
            }
        }
        NotificationExplicitRecipients deduplicated = new NotificationExplicitRecipients();
        deduplicated.setTo(List.copyOf(to));
        deduplicated.setCc(List.copyOf(cc));
        return deduplicated;
    }

    private void logDelivery(NotificationEvent event,
                             NotificationMessage message,
                             NotificationDispatchResult result) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        recipients.addAll(message.getTo());
        recipients.addAll(message.getCc());
        if (recipients.isEmpty()) {
            return;
        }
        for (String recipient : recipients) {
            NotificationDeliveryLog log = new NotificationDeliveryLog();
            log.setEventCode(event.getEventCode());
            log.setReferenceType(event.getReferenceType());
            log.setReferenceId(event.getReferenceId());
            log.setRecipientEmail(recipient);
            log.setChannelType(message.getChannelType());
            log.setStatus(result.status());
            log.setErrorMessage(result.errorMessage());
            log.setCreatedAt(OffsetDateTime.now());
            deliveryLogRepository.save(log);
        }
    }

    private record NotificationContentSource(String subjectTemplate, String bodyTemplate, boolean html) {
    }

    private void cleanupAttachment(NotificationOutbox outbox) {
        try {
            NotificationEvent event = outboxService.readPayload(outbox);
            if (event.getAttachment() == null
                    || !event.getAttachment().isDeleteAfterSend()
                    || event.getAttachment().getPath() == null) {
                return;
            }
            Files.deleteIfExists(Path.of(event.getAttachment().getPath()));
        } catch (Exception ex) {
            LOGGER.warn("Unable to cleanup notification attachment for outbox {}", outbox.getId(), ex);
        }
    }
}
