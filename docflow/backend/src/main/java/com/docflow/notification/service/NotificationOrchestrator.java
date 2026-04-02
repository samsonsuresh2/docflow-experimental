package com.docflow.notification.service;

import com.docflow.notification.model.NotificationEvent;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NotificationOrchestrator {

    private final NotificationOutboxService outboxService;

    public NotificationOrchestrator(NotificationOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    public Optional<Long> publish(NotificationEvent event) {
        return outboxService.enqueue(event);
    }
}
