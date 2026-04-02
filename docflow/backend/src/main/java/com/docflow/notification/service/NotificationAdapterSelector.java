package com.docflow.notification.service;

import com.docflow.notification.model.NotificationChannelType;

import java.util.List;

public interface NotificationAdapterSelector {

    List<String> adapterPriority(NotificationChannelType channelType);
}
