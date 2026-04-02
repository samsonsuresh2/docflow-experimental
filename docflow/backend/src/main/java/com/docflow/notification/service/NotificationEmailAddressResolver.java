package com.docflow.notification.service;

import com.docflow.notification.config.NotificationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Component
public class NotificationEmailAddressResolver {

    private final NotificationProperties properties;

    public NotificationEmailAddressResolver(NotificationProperties properties) {
        this.properties = properties;
    }

    public Optional<String> resolveUserAddress(String userIdOrEmail) {
        if (!StringUtils.hasText(userIdOrEmail)) {
            return Optional.empty();
        }
        String normalized = userIdOrEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("@")) {
            return Optional.of(normalized);
        }
        String domain = properties.getUserEmailDomain();
        if (!StringUtils.hasText(domain)) {
            return Optional.empty();
        }
        return Optional.of(normalized + "@" + domain.trim().toLowerCase(Locale.ROOT));
    }
}
