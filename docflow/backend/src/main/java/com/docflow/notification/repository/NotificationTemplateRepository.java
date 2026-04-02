package com.docflow.notification.repository;

import com.docflow.notification.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {
}
