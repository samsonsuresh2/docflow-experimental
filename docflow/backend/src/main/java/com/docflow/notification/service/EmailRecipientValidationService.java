package com.docflow.notification.service;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationMessage;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EmailRecipientValidationService {

    private static final String ADDRESS_DELIMITER_REGEX = "[,;\\r\\n]+";

    private final NotificationProperties properties;

    public EmailRecipientValidationService(NotificationProperties properties) {
        this.properties = properties;
    }

    public NotificationMessage validateAndNormalize(NotificationMessage message) {
        NotificationMessage normalized = new NotificationMessage();
        normalized.setChannelType(message.getChannelType());
        normalized.setSubject(message.getSubject());
        normalized.setBody(message.getBody());
        normalized.setHtml(message.isHtml());
        normalized.setAttachment(validateAttachment(message.getAttachment()));

        List<String> to = normalizeAddresses(message.getTo(), true);
        List<String> cc = normalizeAddresses(message.getCc(), false);

        LinkedHashSet<String> dedupedTo = new LinkedHashSet<>(to);
        LinkedHashSet<String> dedupedCc = new LinkedHashSet<>();
        for (String address : cc) {
            if (!dedupedTo.contains(address)) {
                dedupedCc.add(address);
            }
        }

        if (dedupedTo.isEmpty()) {
            throw new IllegalArgumentException("At least one To recipient is required.");
        }

        int maxToCount = Math.max(1, properties.getMail().getMaxToCount());
        if (dedupedTo.size() > maxToCount) {
            throw new IllegalArgumentException("To recipient count exceeds the configured maximum of " + maxToCount + ".");
        }

        int maxCcCount = Math.max(0, properties.getMail().getMaxCcCount());
        if (dedupedCc.size() > maxCcCount) {
            throw new IllegalArgumentException("CC recipient count exceeds the configured maximum of " + maxCcCount + ".");
        }

        normalized.setTo(List.copyOf(dedupedTo));
        normalized.setCc(List.copyOf(dedupedCc));
        return normalized;
    }

    private NotificationAttachment validateAttachment(NotificationAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        if (!StringUtils.hasText(attachment.getPath())) {
            throw new IllegalArgumentException("Attachment path is required.");
        }
        if (!Files.isRegularFile(Path.of(attachment.getPath()))) {
            throw new IllegalArgumentException("Attachment file does not exist.");
        }
        return attachment;
    }

    private List<String> normalizeAddresses(List<String> addresses, boolean requiredField) {
        List<String> normalized = new ArrayList<>();
        for (String raw : addresses != null ? addresses : List.<String>of()) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            for (String token : raw.split(ADDRESS_DELIMITER_REGEX)) {
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                String candidate = token.trim().toLowerCase(Locale.ROOT);
                validateEmailAddress(candidate, requiredField);
                normalized.add(candidate);
            }
        }
        return normalized;
    }

    private void validateEmailAddress(String address, boolean requiredField) {
        try {
            InternetAddress internetAddress = new InternetAddress(address, true);
            internetAddress.validate();
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Invalid email address: " + address);
        }

        int atIndex = address.lastIndexOf('@');
        if (atIndex < 0 || atIndex == address.length() - 1) {
            throw new IllegalArgumentException("Invalid email address: " + address);
        }

        String domain = address.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        Set<String> allowedDomains = new LinkedHashSet<>();
        for (String allowed : properties.getMail().getAllowedInternalDomains()) {
            if (StringUtils.hasText(allowed)) {
                allowedDomains.add(allowed.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!allowedDomains.isEmpty() && !allowedDomains.contains(domain)) {
            String label = requiredField ? "To" : "CC";
            throw new IllegalArgumentException(label + " recipient domain is not allowed: " + address);
        }
    }
}
