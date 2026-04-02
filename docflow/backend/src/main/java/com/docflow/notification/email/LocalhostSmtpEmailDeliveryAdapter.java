package com.docflow.notification.email;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import org.springframework.stereotype.Component;

@Component
public class LocalhostSmtpEmailDeliveryAdapter extends AbstractSmtpEmailDeliveryAdapter {

    private final NotificationProperties properties;

    public LocalhostSmtpEmailDeliveryAdapter(NotificationProperties properties) {
        super(properties);
        this.properties = properties;
    }

    @Override
    public String getAdapterName() {
        return "LOCALHOST_SMTP";
    }

    @Override
    public NotificationDispatchResult send(NotificationMessage message) {
        return sendVia(message, properties.getMail().getLocalhostRelay(), getAdapterName());
    }
}
