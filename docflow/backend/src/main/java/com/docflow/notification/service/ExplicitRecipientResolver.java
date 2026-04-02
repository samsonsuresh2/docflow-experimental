package com.docflow.notification.service;

import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.model.NotificationReferenceType;
import com.docflow.notification.model.NotificationRecipientType;
import org.springframework.stereotype.Component;

@Component
public class ExplicitRecipientResolver implements RecipientResolver {

    @Override
    public boolean supports(NotificationReferenceType referenceType) {
        return true;
    }

    @Override
    public NotificationExplicitRecipients resolve(NotificationEvent event, NotificationPolicy policy) {
        NotificationExplicitRecipients source = event.getExplicitRecipients() != null
                ? event.getExplicitRecipients()
                : new NotificationExplicitRecipients();

        NotificationExplicitRecipients resolved = new NotificationExplicitRecipients();
        resolved.setTo(policy.isRecipientEnabled(NotificationRecipientType.EXPLICIT_TO) ? source.getTo() : java.util.List.of());
        resolved.setCc(policy.isRecipientEnabled(NotificationRecipientType.EXPLICIT_CC) ? source.getCc() : java.util.List.of());
        return resolved;
    }
}
