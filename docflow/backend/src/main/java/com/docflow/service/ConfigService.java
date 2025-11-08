package com.docflow.service;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.context.RequestUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface ConfigService {

    @Transactional(readOnly = true)
    String getUploadFieldsConfig();

    @Transactional
    String upsertUploadFieldsConfig(String configJson, RequestUser requestUser);

    @Transactional(readOnly = true)
    String getReviewFilterConfig();

    @Transactional
    String upsertReviewFilterConfig(String configJson, RequestUser requestUser);

    @Transactional(readOnly = true)
    List<FilterDefinition> getReviewFilterDefinitions();
}
