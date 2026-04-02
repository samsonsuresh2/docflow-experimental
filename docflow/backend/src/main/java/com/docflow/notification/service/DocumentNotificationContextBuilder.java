package com.docflow.notification.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.JsonConfig;
import com.docflow.domain.SchemaBindingMode;
import com.docflow.domain.SchemaStatus;
import com.docflow.notification.config.NotificationProperties;
import com.docflow.service.ConfigService;
import com.docflow.service.form.UploadFieldConfigParser;
import com.docflow.service.form.UploadFieldDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class DocumentNotificationContextBuilder {

    private final ConfigService configService;
    private final NotificationProperties properties;
    private final UploadFieldConfigParser uploadFieldConfigParser;

    public DocumentNotificationContextBuilder(ConfigService configService,
                                              NotificationProperties properties,
                                              ObjectMapper objectMapper) {
        this.configService = configService;
        this.properties = properties;
        this.uploadFieldConfigParser = new UploadFieldConfigParser(objectMapper);
    }

    public Map<String, Object> build(DocumentParent document,
                                     DocumentStatus previousStatus,
                                     DocumentStatus currentStatus,
                                     RequestUser actor,
                                     String comment,
                                     Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata != null ? metadata : Map.of();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("documentId", document.getId());
        context.put("documentNumber", document.getDocumentNumber());
        context.put("documentTitle", document.getTitle());
        context.put("fromStatus", previousStatus != null ? previousStatus.name() : null);
        context.put("toStatus", currentStatus != null ? currentStatus.name() : null);
        context.put("maker", document.getCreatedBy());
        context.put("makerUserId", document.getCreatedBy());
        context.put("actingUser", actor != null ? actor.userId() : null);
        context.put("actingRole", actor != null ? actor.activeRole() : null);
        context.put("actorUserId", actor != null ? actor.userId() : null);
        context.put("comment", comment);

        String reviewerUserId = resolveReviewerUserId(safeMetadata, actor, currentStatus);
        String approverUserId = resolveApproverUserId(safeMetadata, actor, currentStatus);
        context.put("reviewer", reviewerUserId);
        context.put("reviewerUserId", reviewerUserId);
        context.put("approver", approverUserId);
        context.put("approverUserId", approverUserId);

        resolveTeamRouting(document, safeMetadata).ifPresent(routing -> {
            context.put("routingFieldName", routing.fieldName());
            context.put("routingFieldValue", routing.fieldValue());
        });

        return context;
    }

    private String resolveReviewerUserId(Map<String, Object> metadata, RequestUser actor, DocumentStatus currentStatus) {
        String metadataValue = findMetadataValue(metadata, properties.getDocument().getReviewerMetadataKeys()).orElse(null);
        if (StringUtils.hasText(metadataValue)) {
            return metadataValue;
        }
        if (actor != null && "REVIEWER".equalsIgnoreCase(actor.activeRole())
                && (currentStatus == DocumentStatus.UNDER_REVIEW || currentStatus == DocumentStatus.REVIEWED
                || currentStatus == DocumentStatus.REJECTED || currentStatus == DocumentStatus.REWORK)) {
            return actor.userId();
        }
        return null;
    }

    private String resolveApproverUserId(Map<String, Object> metadata, RequestUser actor, DocumentStatus currentStatus) {
        String metadataValue = findMetadataValue(metadata, properties.getDocument().getApproverMetadataKeys()).orElse(null);
        if (StringUtils.hasText(metadataValue)) {
            return metadataValue;
        }
        if (actor != null && "APPROVER".equalsIgnoreCase(actor.activeRole())
                && (currentStatus == DocumentStatus.APPROVED || currentStatus == DocumentStatus.REJECTED)) {
            return actor.userId();
        }
        return null;
    }

    private Optional<String> findMetadataValue(Map<String, Object> metadata, List<String> keys) {
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(key.trim()) && entry.getValue() != null) {
                    String text = String.valueOf(entry.getValue()).trim();
                    if (!text.isEmpty()) {
                        return Optional.of(text);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TeamRouting> resolveTeamRouting(DocumentParent document, Map<String, Object> metadata) {
        String rawConfig = resolveDocumentSchemaConfig(document);
        if (!StringUtils.hasText(rawConfig)) {
            return Optional.empty();
        }
        return uploadFieldConfigParser.parse(rawConfig).stream()
                .filter(UploadFieldDefinition::isNotificationTeamRouting)
                .map(UploadFieldDefinition::getName)
                .filter(StringUtils::hasText)
                .findFirst()
                .flatMap(fieldName -> findMetadataEntry(metadata, fieldName).map(value -> new TeamRouting(fieldName, value)));
    }

    private Optional<String> findMetadataEntry(Map<String, Object> metadata, String fieldName) {
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(fieldName.trim()) && entry.getValue() != null) {
                String text = String.valueOf(entry.getValue()).trim();
                if (!text.isEmpty()) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    private String resolveDocumentSchemaConfig(DocumentParent document) {
        JsonConfig binding = new JsonConfig();
        binding.setSchemaVersion(document.getSchemaVersion());
        binding.setSchemaStatus((document.getSchemaBindingMode() == SchemaBindingMode.FLOATING_SANDBOX
                || Integer.valueOf(0).equals(document.getSchemaVersion()))
                ? SchemaStatus.SANDBOX
                : SchemaStatus.ACTIVE);
        return configService.getUploadFieldsConfigForBinding(binding);
    }

    private record TeamRouting(String fieldName, String fieldValue) {
    }
}
