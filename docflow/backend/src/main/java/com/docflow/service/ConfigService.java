package com.docflow.service;

import com.docflow.context.RequestUser;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface ConfigService {

    @Transactional(readOnly = true)
    String getUploadFieldsConfig();

    @Transactional
    String upsertUploadFieldsConfig(String configJson, RequestUser requestUser);
}
