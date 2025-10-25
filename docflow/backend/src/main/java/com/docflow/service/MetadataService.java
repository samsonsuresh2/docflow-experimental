package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Transactional
public interface MetadataService {

    @Transactional
    Map<String, Object> persistMetadata(DocumentParent document, Map<String, Object> requestedMetadata, RequestUser user);

    @Transactional(readOnly = true)
    Map<String, Object> getMetadata(DocumentParent document);
}
