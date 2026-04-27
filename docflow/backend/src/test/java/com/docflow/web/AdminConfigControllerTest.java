package com.docflow.web;

import com.docflow.api.dto.UploadFieldsRequest;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.service.ConfigService;
import com.docflow.service.SchemaBindingStrategy;
import com.docflow.service.UploadSchemaStatusView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminConfigControllerTest {

    private ConfigService configService;
    private RequestUserContext requestUserContext;
    private AdminConfigController controller;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        requestUserContext = mock(RequestUserContext.class);
        controller = new AdminConfigController(configService, requestUserContext);
        when(requestUserContext.requireUser()).thenReturn(new RequestUser("admin1", Set.of("ADMIN"), "ADMIN"));
    }

    @Test
    void getUploadConfigMapsStatusView() {
        when(configService.getUploadSchemaStatus()).thenReturn(statusView());

        var response = controller.getUploadConfig().getBody();

        assertThat(response.bindingStrategy()).isEqualTo(SchemaBindingStrategy.ACTIVE_ONLY);
        assertThat(response.activeVersion()).isEqualTo(3);
        assertThat(response.configJson()).isEqualTo("[]");
    }

    @Test
    void savePromoteAndReleaseUploadConfigDelegateWithCurrentUser() {
        UploadFieldsRequest request = new UploadFieldsRequest();
        request.setConfigJson("[{\"name\":\"x\"}]");
        when(configService.saveSandboxUploadSchema(request.getConfigJson(), user())).thenReturn(request.getConfigJson());
        when(configService.getUploadSchemaStatus()).thenReturn(statusView());

        assertThat(controller.saveUploadConfig(request).getBody().getConfigJson()).isEqualTo(request.getConfigJson());
        assertThat(controller.promoteUploadConfig().getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controller.releaseUploadConfig(request).getBody().activeVersion()).isEqualTo(3);

        verify(configService).promoteSandboxUploadSchema(user());
        verify(configService).releaseActiveUploadSchema(request.getConfigJson(), user());
    }

    @Test
    void reviewFilterEndpointsDelegate() {
        UploadFieldsRequest request = new UploadFieldsRequest();
        request.setConfigJson("{\"filters\":[]}");
        when(configService.getReviewFilterConfig()).thenReturn("{\"filters\":[]}");
        when(configService.upsertReviewFilterConfig(request.getConfigJson(), user())).thenReturn(request.getConfigJson());

        assertThat(controller.getReviewFilterConfig().getBody().getConfigJson()).isEqualTo("{\"filters\":[]}");
        assertThat(controller.saveReviewFilterConfig(request).getBody().getConfigJson()).isEqualTo(request.getConfigJson());
    }

    private UploadSchemaStatusView statusView() {
        return new UploadSchemaStatusView(
            SchemaBindingStrategy.ACTIVE_ONLY,
            3,
            0,
            "[]",
            "admin1",
            "2026-04-27T10:15:30+05:30"
        );
    }

    private RequestUser user() {
        return new RequestUser("admin1", Set.of("ADMIN"), "ADMIN");
    }
}
