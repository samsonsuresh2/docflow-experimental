package com.docflow.service;

import com.docflow.api.dto.RelatedEntityResponse;

public interface RelatedEntityService {

    RelatedEntityResponse getRelatedEntity(Long documentId, String entityName);
}
