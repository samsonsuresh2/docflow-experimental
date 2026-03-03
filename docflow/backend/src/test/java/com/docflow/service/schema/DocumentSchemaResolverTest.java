package com.docflow.service.schema;

import com.docflow.domain.AppConfig;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentSchemaBindingMode;
import com.docflow.domain.UploadSchemaStatus;
import com.docflow.domain.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSchemaResolverTest {

    @Mock
    AppConfigRepository repository;

    private SchemaBindingProperties properties;
    private DocumentSchemaResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SchemaBindingProperties();
        resolver = new DocumentSchemaResolver(repository, properties);
    }

    @Test
    void fixedVersionDocumentStaysOnBoundVersionEvenAfterNewActiveExists() {
        DocumentParent doc = new DocumentParent();
        doc.setSchemaBindingMode(DocumentSchemaBindingMode.FIXED_VERSION);
        doc.setSchemaVersion(1);

        AppConfig versionOne = new AppConfig();
        versionOne.setSchemaVersion(1);
        versionOne.setSchemaStatus(UploadSchemaStatus.DEPRECATED);
        versionOne.setValidationSchemaJson("{\"title\":\"v1\"}");

        when(repository.findFirstByConfigKeyAndSchemaVersionAndSchemaStatusIn(
            UploadSchemaAdminService.UPLOAD_SCHEMA_KEY,
            1,
            List.of(UploadSchemaStatus.ACTIVE, UploadSchemaStatus.DEPRECATED)
        )).thenReturn(Optional.of(versionOne));

        AppConfig resolved = resolver.resolveValidationSchema(doc);
        assertThat(resolved.getValidationSchemaJson()).isEqualTo("{\"title\":\"v1\"}");
    }

    @Test
    void floatingSandboxDocumentAlwaysUsesLatestSandbox() {
        DocumentParent doc = new DocumentParent();
        doc.setSchemaBindingMode(DocumentSchemaBindingMode.FLOATING_SANDBOX);
        doc.setSchemaVersion(0);

        AppConfig sandbox = new AppConfig();
        sandbox.setSchemaVersion(0);
        sandbox.setValidationSchemaJson("{\"title\":\"sandbox-latest\"}");
        when(repository.findFirstByConfigKeyAndSchemaStatus(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX))
            .thenReturn(Optional.of(sandbox));

        AppConfig resolved = resolver.resolveValidationSchema(doc);
        assertThat(resolved.getValidationSchemaJson()).isEqualTo("{\"title\":\"sandbox-latest\"}");
    }

    @Test
    void createInActiveOnlyBindsFixedVersion() {
        properties.setBindingStrategy(SchemaBindingStrategy.ACTIVE_ONLY);
        AppConfig active = new AppConfig();
        active.setSchemaVersion(5);
        when(repository.findFirstByConfigKeyAndSchemaStatus(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY, UploadSchemaStatus.ACTIVE))
            .thenReturn(Optional.of(active));

        DocumentSchemaResolver.SchemaBinding binding = resolver.resolveForCreate();

        assertThat(binding.mode()).isEqualTo(DocumentSchemaBindingMode.FIXED_VERSION);
        assertThat(binding.version()).isEqualTo(5);
    }

    @Test
    void createInSandboxOnlyBindsFloatingSandboxVersion() {
        properties.setBindingStrategy(SchemaBindingStrategy.SANDBOX_ONLY);
        AppConfig sandbox = new AppConfig();
        sandbox.setSchemaVersion(0);
        when(repository.findFirstByConfigKeyAndSchemaStatus(UploadSchemaAdminService.UPLOAD_SCHEMA_KEY, UploadSchemaStatus.SANDBOX))
            .thenReturn(Optional.of(sandbox));

        DocumentSchemaResolver.SchemaBinding binding = resolver.resolveForCreate();

        assertThat(binding.mode()).isEqualTo(DocumentSchemaBindingMode.FLOATING_SANDBOX);
        assertThat(binding.version()).isEqualTo(0);
    }
}
