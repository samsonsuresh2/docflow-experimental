package com.docflow.notification.model;

public record NotificationDispatchResult(
        NotificationDeliveryStatus status,
        String adapterName,
        String errorMessage
) {

    public static NotificationDispatchResult success(String adapterName) {
        return new NotificationDispatchResult(NotificationDeliveryStatus.SUCCESS, adapterName, null);
    }

    public static NotificationDispatchResult failure(String adapterName, String errorMessage) {
        return new NotificationDispatchResult(NotificationDeliveryStatus.FAILED, adapterName, errorMessage);
    }
}
