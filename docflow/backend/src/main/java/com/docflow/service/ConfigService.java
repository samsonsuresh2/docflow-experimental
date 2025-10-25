package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AppConfig;
import com.docflow.domain.repository.AppConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ConfigService {

    public static final String UPLOAD_FIELDS_KEY = "UPLOAD_FIELDS";

    private final AppConfigRepository appConfigRepository;

    public ConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @Transactional(readOnly = true)
    public String getUploadFieldsConfig() {
        return appConfigRepository.findByConfigKey(UPLOAD_FIELDS_KEY)
                .map(AppConfig::getConfigValue)
                .orElse(null);
    }

    @Transactional
    public String upsertUploadFieldsConfig(String configJson, RequestUser requestUser) {
        AppConfig config = appConfigRepository.findByConfigKey(UPLOAD_FIELDS_KEY)
                .orElseGet(AppConfig::new);
        config.setConfigKey(UPLOAD_FIELDS_KEY);
        config.setConfigValue(configJson);
        config.setUpdatedBy(requestUser.userId());
        config.setUpdatedAt(OffsetDateTime.now());
        appConfigRepository.save(config);
        return config.getConfigValue();
    }
}
