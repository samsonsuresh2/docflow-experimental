package com.docflow.notification.service;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationChannelType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PropertyDrivenNotificationAdapterSelector implements NotificationAdapterSelector {

    private final NotificationProperties properties;

    public PropertyDrivenNotificationAdapterSelector(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<String> adapterPriority(NotificationChannelType channelType) {
        if (channelType == NotificationChannelType.EMAIL) {
            return List.copyOf(properties.getMail().getAdapterPriority());
        }
        return List.of();
    }
}
