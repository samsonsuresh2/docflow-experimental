package com.docflow.notification.channel;

import com.docflow.notification.model.NotificationChannelType;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;

public interface NotificationChannel {

    NotificationChannelType getChannelType();

    NotificationDispatchResult send(NotificationMessage message);
}
