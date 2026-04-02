package com.docflow.notification.repository;

import com.docflow.notification.domain.NotificationOutbox;
import com.docflow.notification.model.NotificationOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus status, Pageable pageable);

    Optional<NotificationOutbox> findByIdAndStatus(Long id, NotificationOutboxStatus status);
}
