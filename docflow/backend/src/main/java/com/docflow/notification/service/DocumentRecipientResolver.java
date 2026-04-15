package com.docflow.notification.service;

import com.docflow.domain.AuditCategory;
import com.docflow.domain.DocumentAuditLog;
import com.docflow.domain.repository.DocumentAuditLogRepository;
import com.docflow.notification.domain.NotificationTeamMapping;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.model.NotificationRecipientType;
import com.docflow.notification.model.NotificationReferenceType;
import com.docflow.notification.repository.NotificationTeamMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;

@Component
public class DocumentRecipientResolver implements RecipientResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRecipientResolver.class);

    private final NotificationEmailAddressResolver emailAddressResolver;
    private final NotificationTeamMappingRepository teamMappingRepository;
    private final DocumentAuditLogRepository documentAuditLogRepository;

    public DocumentRecipientResolver(NotificationEmailAddressResolver emailAddressResolver,
                                     NotificationTeamMappingRepository teamMappingRepository,
                                     DocumentAuditLogRepository documentAuditLogRepository) {
        this.emailAddressResolver = emailAddressResolver;
        this.teamMappingRepository = teamMappingRepository;
        this.documentAuditLogRepository = documentAuditLogRepository;
    }

    @Override
    public boolean supports(NotificationReferenceType referenceType) {
        return referenceType == NotificationReferenceType.DOCUMENT;
    }

    @Override
    public NotificationExplicitRecipients resolve(NotificationEvent event, NotificationPolicy policy) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        if (policy.isRecipientEnabled(NotificationRecipientType.MAKER)) {
            addUser(recipients, event.getContext().get("makerUserId"));
        }
        if (policy.isRecipientEnabled(NotificationRecipientType.CURRENT_ACTOR)) {
            addUser(recipients, event.getContext().get("actorUserId"));
        }
        if (policy.isRecipientEnabled(NotificationRecipientType.PAST_PARTICIPANTS)) {
            addPastParticipants(recipients, event);
        }
        if (policy.isRecipientEnabled(NotificationRecipientType.TEAM_DL)) {
            addTeam(recipients, event);
        }

        NotificationExplicitRecipients resolved = new NotificationExplicitRecipients();
        resolved.setTo(java.util.List.copyOf(recipients));
        resolved.setCc(java.util.List.of());
        return resolved;
    }

    private void addUser(LinkedHashSet<String> recipients, Object userId) {
        if (userId == null) {
            return;
        }
        emailAddressResolver.resolveUserAddress(String.valueOf(userId)).ifPresent(recipients::add);
    }

    private void addPastParticipants(LinkedHashSet<String> recipients, NotificationEvent event) {
        Long documentId = parseDocumentId(event.getReferenceId());
        if (documentId == null) {
            return;
        }

        String actorUserId = stringValue(event.getContext().get("actorUserId"));
        OffsetDateTime actionOccurredAt = parseOffsetDateTime(event.getContext().get("actionOccurredAt"));
        List<DocumentAuditLog> auditEntries = actionOccurredAt != null
                ? documentAuditLogRepository.findByDocument_IdAndAuditCategoryAndChangedAtBeforeOrderByChangedAtAsc(
                        documentId,
                        AuditCategory.LIFECYCLE,
                        actionOccurredAt
                )
                : filterLatestCurrentAction(
                        documentAuditLogRepository.findByDocument_IdAndAuditCategoryOrderByChangedAtAsc(documentId, AuditCategory.LIFECYCLE),
                        actorUserId
                );

        for (DocumentAuditLog entry : auditEntries) {
            String changedBy = stringValue(entry.getChangedBy());
            if (!StringUtils.hasText(changedBy)) {
                continue;
            }
            emailAddressResolver.resolveUserAddress(changedBy).ifPresent(recipients::add);
        }
    }

    private void addTeam(LinkedHashSet<String> recipients, NotificationEvent event) {
        String fieldName = stringValue(event.getContext().get("routingFieldName"));
        String fieldValue = stringValue(event.getContext().get("routingFieldValue"));
        if (!StringUtils.hasText(fieldName) || !StringUtils.hasText(fieldValue)) {
            return;
        }
        Optional<NotificationTeamMapping> mapping = teamMappingRepository
                .findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCaseAndActiveTrue(fieldName, fieldValue);
        if (mapping.isPresent()) {
            recipients.add(mapping.get().getTeamEmailDl());
        } else {
            LOGGER.warn("No notification team mapping found for fieldName={} fieldValue={} referenceId={}",
                    fieldName, fieldValue, event.getReferenceId());
        }
    }

    private String stringValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String text = String.valueOf(rawValue).trim();
        return text.isEmpty() ? null : text;
    }

    private Long parseDocumentId(String referenceId) {
        try {
            return StringUtils.hasText(referenceId) ? Long.valueOf(referenceId.trim()) : null;
        } catch (NumberFormatException ex) {
            LOGGER.warn("Unable to parse document notification referenceId={} for past participant lookup", referenceId);
            return null;
        }
    }

    private OffsetDateTime parseOffsetDateTime(Object rawValue) {
        String text = stringValue(rawValue);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            LOGGER.warn("Unable to parse actionOccurredAt={} for document notification", text);
            return null;
        }
    }

    private List<DocumentAuditLog> filterLatestCurrentAction(List<DocumentAuditLog> auditEntries, String actorUserId) {
        if (auditEntries == null || auditEntries.isEmpty()) {
            return List.of();
        }
        OffsetDateTime latestChangedAt = auditEntries.stream()
                .map(DocumentAuditLog::getChangedAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        if (latestChangedAt == null || !StringUtils.hasText(actorUserId)) {
            return auditEntries;
        }
        return auditEntries.stream()
                .filter(entry -> !(latestChangedAt.equals(entry.getChangedAt())
                        && actorUserId.equalsIgnoreCase(stringValue(entry.getChangedBy()))))
                .toList();
    }
}
