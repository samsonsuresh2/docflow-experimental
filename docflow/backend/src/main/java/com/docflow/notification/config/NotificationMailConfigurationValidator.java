package com.docflow.notification.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class NotificationMailConfigurationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationMailConfigurationValidator.class);

    private final NotificationProperties properties;

    public NotificationMailConfigurationValidator(NotificationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }

        String userEmailDomain = normalize(properties.getUserEmailDomain());
        String fromAddress = normalize(properties.getMail().getFromAddress());
        String replyTo = normalize(properties.getMail().getReplyTo());
        Set<String> allowedDomains = new LinkedHashSet<>();
        for (String domain : properties.getMail().getAllowedInternalDomains()) {
            String normalized = normalize(domain);
            if (normalized != null) {
                allowedDomains.add(normalized);
            }
        }

        LOGGER.info(
                "Notification mail config loaded. fromAddress={} replyTo={} userEmailDomain={} allowedInternalDomains={} adapterPriority={}",
                fromAddress != null ? fromAddress : "(missing)",
                replyTo != null ? replyTo : "(none)",
                userEmailDomain != null ? userEmailDomain : "(missing)",
                allowedDomains,
                properties.getMail().getAdapterPriority()
        );

        if (!StringUtils.hasText(fromAddress)) {
            throw new IllegalStateException("docflow.notification.mail.from-address must be configured.");
        }
        if (!StringUtils.hasText(userEmailDomain)) {
            throw new IllegalStateException("docflow.notification.user-email-domain must be configured.");
        }
        if (allowedDomains.isEmpty()) {
            throw new IllegalStateException("docflow.notification.mail.allowed-internal-domains must contain at least one domain.");
        }

        String fromDomain = extractDomain(fromAddress);
        if (!userEmailDomain.equals(fromDomain)) {
            throw new IllegalStateException("docflow.notification.mail.from-address must use docflow.notification.user-email-domain.");
        }
        if (!allowedDomains.contains(fromDomain)) {
            throw new IllegalStateException("docflow.notification.mail.from-address must belong to allowed-internal-domains.");
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String extractDomain(String address) {
        int atIndex = address.lastIndexOf('@');
        if (atIndex < 0 || atIndex == address.length() - 1) {
            throw new IllegalStateException("docflow.notification.mail.from-address must be a valid email address.");
        }
        return address.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }
}
