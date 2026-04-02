package com.docflow.notification.email;

import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;

public interface EmailDeliveryAdapter {

    String getAdapterName();

    NotificationDispatchResult send(NotificationMessage message);
}
