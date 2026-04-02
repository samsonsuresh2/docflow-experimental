package com.docflow.notification.repository;

import com.docflow.notification.domain.NotificationEventConfig;
import com.docflow.notification.model.NotificationEventCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventConfigRepository extends JpaRepository<NotificationEventConfig, NotificationEventCode> {
}
