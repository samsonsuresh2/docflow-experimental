package com.docflow.service;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.context.RequestUser;
import com.docflow.domain.JsonConfig;
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

    @Transactional(readOnly = true)
    String getUploadFieldsConfigForBinding(JsonConfig binding);

    @Transactional(readOnly = true)
    JsonConfig resolveUploadSchemaForNewDocument();

    @Transactional
    String saveSandboxUploadSchema(String configJson, RequestUser requestUser);

    @Transactional
    void promoteSandboxUploadSchema(RequestUser requestUser);

    @Transactional
    void releaseActiveUploadSchema(String configJson, RequestUser requestUser);

    @Transactional(readOnly = true)
    UploadSchemaStatusView getUploadSchemaStatus();
}
