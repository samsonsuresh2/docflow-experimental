package com.docflow.notification.service;

import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.model.NotificationReferenceType;

public interface RecipientResolver {

    boolean supports(NotificationReferenceType referenceType);

    NotificationExplicitRecipients resolve(NotificationEvent event, NotificationPolicy policy);
}
