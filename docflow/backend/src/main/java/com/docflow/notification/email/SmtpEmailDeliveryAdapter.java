package com.docflow.notification.email;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailDeliveryAdapter extends AbstractSmtpEmailDeliveryAdapter {

    private final NotificationProperties properties;

    public SmtpEmailDeliveryAdapter(NotificationProperties properties) {
        super(properties);
        this.properties = properties;
    }

    @Override
    public String getAdapterName() {
        return "SMTP";
    }

    @Override
    public NotificationDispatchResult send(NotificationMessage message) {
        return sendVia(message, properties.getMail().getSmtp(), getAdapterName());
    }
}
