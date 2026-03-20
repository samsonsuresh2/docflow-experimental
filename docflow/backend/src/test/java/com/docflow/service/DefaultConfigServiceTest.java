package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.JsonConfig;
import com.docflow.domain.SchemaStatus;
import com.docflow.domain.repository.JsonConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultConfigServiceTest {

    @Mock
    private JsonConfigRepository jsonConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SchemaBindingProperties schemaBindingProperties = new SchemaBindingProperties();

    @InjectMocks
    private DefaultConfigService service;

    @Captor
    private ArgumentCaptor<JsonConfig> configCaptor;

    @BeforeEach
    void setUp() {
        service = new DefaultConfigService(jsonConfigRepository, objectMapper, schemaBindingProperties);
    }

    @Test
    void releaseActiveUploadSchemaCreatesNewActiveAndDeprecatesPrevious() {
        schemaBindingProperties.setBindingStrategy(SchemaBindingStrategy.ACTIVE_ONLY);
        JsonConfig currentActive = new JsonConfig();
        currentActive.setConfigKey(DefaultConfigService.UPLOAD_CONFIG_KEY);
        currentActive.setSchemaStatus(SchemaStatus.ACTIVE);
        currentActive.setSchemaVersion(2);
        currentActive.setConfigValue("[{\"name\":\"legacy\"}]");

        when(jsonConfigRepository.findLatestByConfigKeyAndSchemaStatus(DefaultConfigService.UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE))
            .thenReturn(List.of(currentActive));
        when(jsonConfigRepository.save(any(JsonConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.releaseActiveUploadSchema("{\"fields\":[{\"name\":\"customerId\"}]}", new RequestUser("admin1", Set.of("ADMIN"), "ADMIN"));

        org.mockito.Mockito.verify(jsonConfigRepository, org.mockito.Mockito.times(2)).save(configCaptor.capture());
        List<JsonConfig> saved = configCaptor.getAllValues();

        JsonConfig newActive = saved.get(0);
        assertThat(newActive.getSchemaStatus()).isEqualTo(SchemaStatus.ACTIVE);
        assertThat(newActive.getSchemaVersion()).isEqualTo(3);
        assertThat(newActive.getConfigValue()).isEqualTo("{\"fields\":[{\"name\":\"customerId\"}]}");
        assertThat(newActive.getUpdatedBy()).isEqualTo("admin1");

        JsonConfig deprecated = saved.get(1);
        assertThat(deprecated).isSameAs(currentActive);
        assertThat(deprecated.getSchemaStatus()).isEqualTo(SchemaStatus.DEPRECATED);
        assertThat(deprecated.getUpdatedBy()).isEqualTo("admin1");
    }

    @Test
    void releaseActiveUploadSchemaRejectsSandboxMode() {
        schemaBindingProperties.setBindingStrategy(SchemaBindingStrategy.SANDBOX_ONLY);

        assertThatThrownBy(() -> service.releaseActiveUploadSchema("[]", new RequestUser("admin1", Set.of("ADMIN"), "ADMIN")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("ACTIVE_ONLY");
    }

    @Test
    void saveSandboxUploadSchemaRejectsInvalidShape() {
        assertThatThrownBy(() -> service.saveSandboxUploadSchema("{\"invalid\":true}", new RequestUser("admin1", Set.of("ADMIN"), "ADMIN")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("fields array");
    }

    @Test
    void promoteSandboxUploadSchemaUsesValidatedSandboxJson() {
        JsonConfig sandbox = new JsonConfig();
        sandbox.setConfigKey(DefaultConfigService.UPLOAD_CONFIG_KEY);
        sandbox.setSchemaStatus(SchemaStatus.SANDBOX);
        sandbox.setSchemaVersion(0);
        sandbox.setConfigValue("[{\"name\":\"sandboxField\"}]");
        when(jsonConfigRepository.findByConfigKeyAndSchemaStatus(DefaultConfigService.UPLOAD_CONFIG_KEY, SchemaStatus.SANDBOX))
            .thenReturn(Optional.of(sandbox));
        when(jsonConfigRepository.findLatestByConfigKeyAndSchemaStatus(DefaultConfigService.UPLOAD_CONFIG_KEY, SchemaStatus.ACTIVE))
            .thenReturn(List.of());
        when(jsonConfigRepository.save(any(JsonConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.promoteSandboxUploadSchema(new RequestUser("admin1", Set.of("ADMIN"), "ADMIN"));

        org.mockito.Mockito.verify(jsonConfigRepository).save(configCaptor.capture());
        JsonConfig promoted = configCaptor.getValue();
        assertThat(promoted.getSchemaStatus()).isEqualTo(SchemaStatus.ACTIVE);
        assertThat(promoted.getSchemaVersion()).isEqualTo(1);
        assertThat(promoted.getConfigValue()).isEqualTo("[{\"name\":\"sandboxField\"}]");
    }
}
