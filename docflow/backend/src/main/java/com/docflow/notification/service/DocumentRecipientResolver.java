package com.docflow.notification.service;

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

import java.util.LinkedHashSet;
import java.util.Optional;

@Component
public class DocumentRecipientResolver implements RecipientResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRecipientResolver.class);

    private final NotificationEmailAddressResolver emailAddressResolver;
    private final NotificationTeamMappingRepository teamMappingRepository;

    public DocumentRecipientResolver(NotificationEmailAddressResolver emailAddressResolver,
                                     NotificationTeamMappingRepository teamMappingRepository) {
        this.emailAddressResolver = emailAddressResolver;
        this.teamMappingRepository = teamMappingRepository;
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
        if (policy.isRecipientEnabled(NotificationRecipientType.CURRENT_REVIEWER)) {
            addUser(recipients, event.getContext().get("reviewerUserId"));
        }
        if (policy.isRecipientEnabled(NotificationRecipientType.CURRENT_APPROVER)) {
            addUser(recipients, event.getContext().get("approverUserId"));
        }
        if (policy.isRecipientEnabled(NotificationRecipientType.CURRENT_ACTOR)) {
            addUser(recipients, event.getContext().get("actorUserId"));
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
}
