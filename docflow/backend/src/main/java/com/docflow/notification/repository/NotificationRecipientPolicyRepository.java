package com.docflow.notification.repository;

import com.docflow.notification.domain.NotificationRecipientPolicyEntity;
import com.docflow.notification.domain.NotificationRecipientPolicyId;
import com.docflow.notification.model.NotificationEventCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecipientPolicyRepository extends JpaRepository<NotificationRecipientPolicyEntity, NotificationRecipientPolicyId> {

    List<NotificationRecipientPolicyEntity> findByIdEventCode(NotificationEventCode eventCode);
}
