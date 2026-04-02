package com.docflow.notification.service;

import com.docflow.notification.config.NotificationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxProcessor {

    private final NotificationProperties properties;
    private final NotificationOutboxService outboxService;
    private final NotificationDeliveryService deliveryService;

    public NotificationOutboxProcessor(NotificationProperties properties,
                                       NotificationOutboxService outboxService,
                                       NotificationDeliveryService deliveryService) {
        this.properties = properties;
        this.outboxService = outboxService;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${docflow.notification.outbox.fixed-delay-ms:30000}")
    public void processPending() {
        if (!properties.isEnabled() || !properties.getOutbox().isProcessingEnabled()) {
            return;
        }
        int batchSize = Math.max(1, properties.getOutbox().getBatchSize());
        for (Long outboxId : outboxService.findPendingIds(batchSize)) {
            deliveryService.processOutbox(outboxId);
        }
    }
}
