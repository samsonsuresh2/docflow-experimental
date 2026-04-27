package com.docflow.reports.service;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationPolicy;
import com.docflow.notification.render.TemplateRenderer;
import com.docflow.notification.service.EmailRecipientValidationService;
import com.docflow.notification.service.NotificationOrchestrator;
import com.docflow.notification.service.NotificationPolicyService;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportMailConfig;
import com.docflow.reports.dto.ReportMailFieldConfig;
import com.docflow.reports.dto.ReportMailMode;
import com.docflow.reports.dto.ReportTemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportMailServiceTest {

    private ReportTemplateService templateService;
    private ReportExecutionService executionService;
    private ReportMailComposer reportMailComposer;
    private EmailRecipientValidationService emailRecipientValidationService;
    private NotificationPolicyService notificationPolicyService;
    private NotificationOrchestrator notificationOrchestrator;
    private RequestUserContext requestUserContext;
    private ReportMailService service;

    @BeforeEach
    void setUp() {
        templateService = mock(ReportTemplateService.class);
        executionService = mock(ReportExecutionService.class);
        reportMailComposer = mock(ReportMailComposer.class);
        emailRecipientValidationService = mock(EmailRecipientValidationService.class);
        notificationPolicyService = mock(NotificationPolicyService.class);
        notificationOrchestrator = mock(NotificationOrchestrator.class);
        requestUserContext = mock(RequestUserContext.class);
        service = new ReportMailService(
                templateService,
                executionService,
                reportMailComposer,
                new TemplateRenderer(),
                emailRecipientValidationService,
                notificationPolicyService,
                notificationOrchestrator,
                requestUserContext
        );

        NotificationPolicy policy = new NotificationPolicy();
        policy.setEnabled(true);
        when(notificationPolicyService.resolve(any())).thenReturn(Optional.of(policy));
        when(notificationOrchestrator.publish(any(NotificationEvent.class))).thenReturn(Optional.of(11L));
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(new RequestUser("maker1", Set.of("MAKER"), "MAKER")));
        when(executionService.runAll(any(), anyInt())).thenReturn(new ReportExecutionModels.RunResponse(
                List.of("documentNumber", "status"),
                List.of(Map.of("documentNumber", "DOC-1", "status", "APPROVED")),
                1
        ));
    }

    @Test
    void enforcesMandatoryRecipientsAndDisclaimerBeforeQueueing() {
        ReportExecutionModels.MailRequest request = new ReportExecutionModels.MailRequest();
        request.setTemplateId(10L);
        request.setDeliveryMode(ReportExecutionModels.ReportMailDeliveryMode.INLINE);
        request.setFilterSummary("status = APPROVED");
        ReportExecutionModels.MailFields fields = new ReportExecutionModels.MailFields();
        fields.setTo("user@company.internal");
        fields.setBody("Body");
        request.setFields(fields);

        when(templateService.getById(10L)).thenReturn(templateResponse(reportMailConfig(ReportMailMode.INLINE_ONLY)));
        when(reportMailComposer.buildInlineHtml(any(), any(), any())).thenReturn("<p>inline</p>");

        service.enqueue(request);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationOrchestrator).publish(captor.capture());
        NotificationEvent event = captor.getValue();
        assertThat(event.getExplicitRecipients().getTo()).containsExactly("user@company.internal", "ops@company.internal");
        assertThat(event.getContentOverride().getSubjectTemplate()).contains("Loan Summary");
        assertThat(event.getContentOverride().getBodyTemplate()).isEqualTo("<p>inline</p>");
        assertThat(event.getContext()).containsEntry("triggeredBy", "maker1");
    }

    @Test
    void fallsBackToInlineWhenAttachmentFailsAndInlineIsAllowed() throws Exception {
        ReportExecutionModels.MailRequest request = new ReportExecutionModels.MailRequest();
        request.setTemplateId(10L);
        request.setDeliveryMode(ReportExecutionModels.ReportMailDeliveryMode.ATTACHMENT);
        ReportExecutionModels.MailFields fields = new ReportExecutionModels.MailFields();
        fields.setTo("user@company.internal");
        request.setFields(fields);

        when(templateService.getById(10L)).thenReturn(templateResponse(reportMailConfig(ReportMailMode.INLINE_OR_ATTACHMENT)));
        when(reportMailComposer.createAttachment(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        when(reportMailComposer.buildInlineHtml(any(), any(), any())).thenReturn("<p>fallback-inline</p>");

        service.enqueue(request);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationOrchestrator).publish(captor.capture());
        assertThat(captor.getValue().getAttachment()).isNull();
        assertThat(captor.getValue().getContentOverride().getBodyTemplate()).isEqualTo("<p>fallback-inline</p>");
        assertThat(captor.getValue().getContext()).containsEntry("attachmentFallback", "INLINE");
    }

    private ReportTemplateResponse templateResponse(ReportMailConfig mailConfig) {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.DOCUMENT_NUMBER"));
        request.setMail(mailConfig);
        return new ReportTemplateResponse(10L, "Loan Summary", null, request, "admin1", Instant.now());
    }

    private ReportMailConfig reportMailConfig(ReportMailMode mode) {
        ReportMailConfig config = new ReportMailConfig();
        config.setEnabled(true);
        config.setMode(mode);

        ReportMailFieldConfig to = new ReportMailFieldConfig();
        to.setMandatory("ops@company.internal");
        to.setEditable(true);
        config.setTo(to);

        ReportMailFieldConfig subject = new ReportMailFieldConfig();
        subject.setDefaultValue("Report ${reportName}");
        subject.setEditable(false);
        config.setSubject(subject);

        ReportMailFieldConfig body = new ReportMailFieldConfig();
        body.setDefaultValue("Hello ${triggeredBy}");
        config.setBody(body);

        ReportMailFieldConfig disclaimer = new ReportMailFieldConfig();
        disclaimer.setMandatory("Internal use only");
        config.setDisclaimer(disclaimer);
        return config;
    }
}
