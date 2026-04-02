package com.docflow.notification.service;

import com.docflow.notification.domain.NotificationEventConfig;
import com.docflow.notification.domain.NotificationRecipientPolicyEntity;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.repository.NotificationEventConfigRepository;
import com.docflow.notification.repository.NotificationRecipientPolicyRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Optional;

@Service
public class NotificationPolicyService {

    private final NotificationEventConfigRepository eventConfigRepository;
    private final NotificationRecipientPolicyRepository recipientPolicyRepository;

    public NotificationPolicyService(NotificationEventConfigRepository eventConfigRepository,
                                     NotificationRecipientPolicyRepository recipientPolicyRepository) {
        this.eventConfigRepository = eventConfigRepository;
        this.recipientPolicyRepository = recipientPolicyRepository;
    }

    public Optional<NotificationPolicy> resolve(NotificationEventCode eventCode) {
        return eventConfigRepository.findById(eventCode)
                .map(this::toPolicy);
    }

    private NotificationPolicy toPolicy(NotificationEventConfig config) {
        NotificationPolicy policy = new NotificationPolicy();
        policy.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        policy.setChannelType(config.getChannelType());
        policy.setTemplateCode(config.getTemplateCode());
        policy.setSendForBulk(Boolean.TRUE.equals(config.getSendForBulk()));

        EnumMap<com.docflow.notification.model.NotificationRecipientType, Boolean> policies =
                new EnumMap<>(com.docflow.notification.model.NotificationRecipientType.class);
        for (NotificationRecipientPolicyEntity entity : recipientPolicyRepository.findByIdEventCode(config.getEventCode())) {
            policies.put(entity.getId().getRecipientType(), Boolean.TRUE.equals(entity.getEnabled()));
        }
        policy.setRecipientPolicies(policies);
        return policy;
    }
}
