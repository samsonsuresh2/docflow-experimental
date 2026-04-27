package com.docflow.notification.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.SchemaBindingMode;
import com.docflow.service.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentNotificationContextBuilderTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final DocumentNotificationContextBuilder builder =
        new DocumentNotificationContextBuilder(configService, new ObjectMapper());

    @Test
    void buildIncludesDocumentActorStatusAndRoutingContext() {
        when(configService.getUploadFieldsConfigForBinding(any())).thenReturn("""
            [
              {"name":"branch","notificationTeamRouting":true},
              {"name":"ignored","notificationTeamRouting":false}
            ]
            """);
        DocumentParent document = document();
        OffsetDateTime when = OffsetDateTime.parse("2026-04-27T10:15:30+05:30");

        Map<String, Object> context = builder.build(
            document,
            DocumentStatus.OPEN,
            DocumentStatus.APPROVED,
            new RequestUser("approver1", Set.of("APPROVER"), "APPROVER"),
            "approved",
            Map.of(" Branch ", " Retail "),
            when
        );

        assertThat(context)
            .containsEntry("documentId", 10L)
            .containsEntry("documentNumber", "DOC-10")
            .containsEntry("documentTitle", "Loan")
            .containsEntry("fromStatus", "OPEN")
            .containsEntry("toStatus", "APPROVED")
            .containsEntry("maker", "maker1")
            .containsEntry("actingUser", "approver1")
            .containsEntry("actingRole", "APPROVER")
            .containsEntry("comment", "approved")
            .containsEntry("actionOccurredAt", when.toString())
            .containsEntry("routingFieldName", "branch")
            .containsEntry("routingFieldValue", "Retail");
    }

    @Test
    void buildOmitsRoutingWhenConfigOrMetadataDoesNotResolve() {
        when(configService.getUploadFieldsConfigForBinding(any())).thenReturn("");

        Map<String, Object> context = builder.build(document(), null, null, null, null, null, null);

        assertThat(context).containsEntry("fromStatus", null).containsEntry("toStatus", null);
        assertThat(context).doesNotContainKeys("routingFieldName", "routingFieldValue");
    }

    private DocumentParent document() {
        DocumentParent document = new DocumentParent();
        ReflectionTestUtils.setField(document, "id", 10L);
        document.setDocumentNumber("DOC-10");
        document.setTitle("Loan");
        document.setCreatedBy("maker1");
        document.setSchemaVersion(0);
        document.setSchemaBindingMode(SchemaBindingMode.FLOATING_SANDBOX);
        return document;
    }
}
