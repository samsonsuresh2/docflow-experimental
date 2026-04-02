package com.docflow.notification.email;

import com.docflow.notification.channel.NotificationChannel;
import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import com.docflow.notification.service.EmailRecipientValidationService;
import com.docflow.notification.service.NotificationAdapterSelector;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final NotificationAdapterSelector adapterSelector;
    private final EmailRecipientValidationService validationService;
    private final Map<String, EmailDeliveryAdapter> adaptersByName;

    public EmailNotificationChannel(NotificationAdapterSelector adapterSelector,
                                    EmailRecipientValidationService validationService,
                                    List<EmailDeliveryAdapter> adapters) {
        this.adapterSelector = adapterSelector;
        this.validationService = validationService;
        this.adaptersByName = new HashMap<>();
        for (EmailDeliveryAdapter adapter : adapters) {
            adaptersByName.put(adapter.getAdapterName().toUpperCase(), adapter);
        }
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public NotificationDispatchResult send(NotificationMessage message) {
        NotificationMessage normalizedMessage;
        try {
            normalizedMessage = validationService.validateAndNormalize(message);
        } catch (IllegalArgumentException ex) {
            return NotificationDispatchResult.failure("VALIDATION", ex.getMessage());
        }

        NotificationDispatchResult lastFailure = null;
        for (String adapterName : adapterSelector.adapterPriority(NotificationChannelType.EMAIL)) {
            EmailDeliveryAdapter adapter = adaptersByName.get(adapterName.toUpperCase());
            if (adapter == null) {
                continue;
            }
            NotificationDispatchResult result = adapter.send(normalizedMessage);
            if (result.status() == com.docflow.notification.model.NotificationDeliveryStatus.SUCCESS) {
                return result;
            }
            lastFailure = result;
        }
        return lastFailure != null
                ? lastFailure
                : NotificationDispatchResult.failure("EMAIL", "No email delivery adapter is configured");
    }
}
