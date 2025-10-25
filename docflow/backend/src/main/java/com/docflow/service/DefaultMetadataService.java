package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentMetadata;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.repository.DocumentMetadataRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DefaultMetadataService implements MetadataService {

    private final DocumentMetadataRepository metadataRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DefaultMetadataService(DocumentMetadataRepository metadataRepository,
                                  AuditService auditService,
                                  ObjectMapper objectMapper) {
        this.metadataRepository = metadataRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> persistMetadata(DocumentParent document, Map<String, Object> requestedMetadata, RequestUser user) {
        List<DocumentMetadata> existing = metadataRepository.findByDocument(document);
        Map<String, DocumentMetadata> byKey = existing.stream()
                .collect(Collectors.toMap(DocumentMetadata::getFieldKey, entry -> entry, (left, right) -> left));

        Map<String, Object> safeMetadata = requestedMetadata != null ? requestedMetadata : Collections.emptyMap();
        OffsetDateTime now = OffsetDateTime.now();
        Set<String> processed = new HashSet<>();

        for (Map.Entry<String, Object> entry : safeMetadata.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            String serializedValue = serializeValue(newValue);
            DocumentMetadata current = byKey.get(key);
            if (current == null) {
                DocumentMetadata created = new DocumentMetadata();
                created.setDocument(document);
                created.setFieldKey(key);
                created.setFieldValue(serializedValue);
                created.setCreatedBy(user.userId());
                created.setCreatedAt(now);
                metadataRepository.save(created);
                auditService.logFieldUpdate(document, key, null, newValue, "ADDED", user, now);
            } else if (!Objects.equals(current.getFieldValue(), serializedValue)) {
                Object oldValue = deserializeValue(current.getFieldValue());
                current.setFieldValue(serializedValue);
                current.setCreatedBy(user.userId());
                current.setCreatedAt(now);
                metadataRepository.save(current);
                auditService.logFieldUpdate(document, key, oldValue, newValue, "UPDATED", user, now);
            }
            processed.add(key);
        }

        for (DocumentMetadata metadata : existing) {
            if (!processed.contains(metadata.getFieldKey())) {
                metadataRepository.delete(metadata);
                Object oldValue = deserializeValue(metadata.getFieldValue());
                auditService.logFieldUpdate(document, metadata.getFieldKey(), oldValue, null, "REMOVED", user, now);
            }
        }

        return getMetadata(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getMetadata(DocumentParent document) {
        return metadataRepository.findByDocument(document).stream()
                .collect(Collectors.toMap(DocumentMetadata::getFieldKey,
                        entry -> deserializeValue(entry.getFieldValue()),
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize metadata value", e);
        }
    }

    private Object deserializeValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            return value;
        }
    }
}
