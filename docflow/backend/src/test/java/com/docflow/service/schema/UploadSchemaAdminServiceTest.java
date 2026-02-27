package com.docflow.service.schema;

import com.docflow.context.RequestUser;
import com.docflow.domain.AppConfig;
import com.docflow.domain.UploadSchemaStatus;
import com.docflow.domain.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadSchemaAdminServiceTest {

    @Mock
    AppConfigRepository repository;

    @InjectMocks
    UploadSchemaAdminService service;

    @Test
    void promoteSandboxCreatesNewActiveAndDeprecatesOldActive() {
        AppConfig sandbox = new AppConfig();
        sandbox.setConfigKey(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY);
        sandbox.setSchemaStatus(UploadSchemaStatus.SANDBOX);
        sandbox.setSchemaVersion(0);
        sandbox.setConfigValue("sandbox-admin");
        sandbox.setValidationSchemaJson("sandbox-schema");

        AppConfig oldActive = new AppConfig();
        oldActive.setConfigKey(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY);
        oldActive.setSchemaStatus(UploadSchemaStatus.ACTIVE);
        oldActive.setSchemaVersion(3);

        when(repository.findFirstByConfigKeyAndSchemaStatus(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX))
            .thenReturn(Optional.of(sandbox));
        when(repository.findFirstByConfigKeyAndSchemaStatus(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY, UploadSchemaStatus.ACTIVE))
            .thenReturn(Optional.of(oldActive));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.findByConfigKeyAndSchemaStatus(any(), any())).thenReturn(java.util.List.of());

        AppConfig newActive = service.promoteSandboxToActive(new RequestUser("admin", Set.of("ADMIN"), "ADMIN"));

        ArgumentCaptor<AppConfig> cap = ArgumentCaptor.forClass(AppConfig.class);
        verify(repository, atLeast(2)).save(cap.capture());
        assertThat(cap.getAllValues().stream().anyMatch(c -> c.getSchemaStatus() == UploadSchemaStatus.DEPRECATED && c.getSchemaVersion() == 3)).isTrue();
        assertThat(newActive.getSchemaStatus()).isEqualTo(UploadSchemaStatus.ACTIVE);
        assertThat(newActive.getSchemaVersion()).isEqualTo(4);
        assertThat(newActive.getConfigValue()).isEqualTo("sandbox-admin");
    }
}
