package com.docflow.notification.repository;

import com.docflow.notification.domain.NotificationDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {
}
