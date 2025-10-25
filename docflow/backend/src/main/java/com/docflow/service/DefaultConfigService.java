package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AppConfig;
import com.docflow.domain.repository.AppConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional
public class DefaultConfigService implements ConfigService {

    public static final String UPLOAD_FIELDS_KEY = "UPLOAD_FIELDS";

    private final AppConfigRepository appConfigRepository;

    public DefaultConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public String getUploadFieldsConfig() {
        return appConfigRepository.findByConfigKey(UPLOAD_FIELDS_KEY)
                .map(AppConfig::getConfigValue)
                .orElse(null);
    }

    @Override
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
