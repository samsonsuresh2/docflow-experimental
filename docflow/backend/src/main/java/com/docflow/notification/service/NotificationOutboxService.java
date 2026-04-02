package com.docflow.notification.service;

import com.docflow.notification.domain.NotificationOutbox;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationOutboxStatus;
import com.docflow.notification.repository.NotificationOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationOutboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxService.class);

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NotificationOutboxService(NotificationOutboxRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<Long> enqueue(NotificationEvent event) {
        try {
            NotificationOutbox outbox = new NotificationOutbox();
            outbox.setEventType(event.getEventType());
            outbox.setEventCode(event.getEventCode());
            outbox.setReferenceType(event.getReferenceType());
            outbox.setReferenceId(event.getReferenceId());
            outbox.setPayloadJson(objectMapper.writeValueAsString(event));
            outbox.setStatus(NotificationOutboxStatus.PENDING);
            outbox.setAttemptCount(0);
            outbox.setLastError(null);
            outbox.setCreatedAt(OffsetDateTime.now());
            outbox.setProcessedAt(null);
            return Optional.of(outboxRepository.save(outbox).getId());
        } catch (JsonProcessingException ex) {
            LOGGER.error("Failed to serialize notification event {}", event != null ? event.getEventCode() : null, ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to persist notification outbox event {}", event != null ? event.getEventCode() : null, ex);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingIds(int batchSize) {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, batchSize))
                .stream()
                .map(NotificationOutbox::getId)
                .toList();
    }

    @Transactional
    public Optional<NotificationOutbox> markProcessing(Long outboxId) {
        Optional<NotificationOutbox> outboxOptional = outboxRepository.findByIdAndStatus(outboxId, NotificationOutboxStatus.PENDING);
        outboxOptional.ifPresent(outbox -> {
            outbox.setStatus(NotificationOutboxStatus.PROCESSING);
            outbox.setAttemptCount((outbox.getAttemptCount() != null ? outbox.getAttemptCount() : 0) + 1);
            outboxRepository.save(outbox);
        });
        return outboxOptional;
    }

    @Transactional
    public void markSent(Long outboxId) {
        outboxRepository.findById(outboxId).ifPresent(outbox -> {
            outbox.setStatus(NotificationOutboxStatus.SENT);
            outbox.setLastError(null);
            outbox.setProcessedAt(OffsetDateTime.now());
            outboxRepository.save(outbox);
        });
    }

    @Transactional
    public void markFailed(Long outboxId, String errorMessage) {
        outboxRepository.findById(outboxId).ifPresent(outbox -> {
            outbox.setStatus(NotificationOutboxStatus.FAILED);
            outbox.setLastError(truncate(errorMessage));
            outbox.setProcessedAt(OffsetDateTime.now());
            outboxRepository.save(outbox);
        });
    }

    public NotificationEvent readPayload(NotificationOutbox outbox) {
        try {
            return objectMapper.readValue(outbox.getPayloadJson(), NotificationEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read notification payload", ex);
        }
    }

    private String truncate(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 4000 ? errorMessage.substring(0, 4000) : errorMessage;
    }
}
