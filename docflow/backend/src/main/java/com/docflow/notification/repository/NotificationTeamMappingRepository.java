package com.docflow.notification.repository;

import com.docflow.notification.domain.NotificationTeamMapping;
import com.docflow.notification.domain.NotificationTeamMappingId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTeamMappingRepository extends JpaRepository<NotificationTeamMapping, NotificationTeamMappingId> {

    Optional<NotificationTeamMapping> findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCase(String fieldName, String fieldValue);

    Optional<NotificationTeamMapping> findByIdFieldNameIgnoreCaseAndIdFieldValueIgnoreCaseAndActiveTrue(String fieldName, String fieldValue);
}
