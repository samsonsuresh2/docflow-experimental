package com.docflow.reports.service;

import com.docflow.context.RequestUserContext;
import com.docflow.notification.model.NotificationContent;
import com.docflow.notification.model.NotificationEvent;
import com.docflow.notification.model.NotificationEventCode;
import com.docflow.notification.model.NotificationEventType;
import com.docflow.notification.model.NotificationExplicitRecipients;
import com.docflow.notification.model.NotificationMessage;
import com.docflow.notification.model.NotificationReferenceType;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportMailService {

    private static final DateTimeFormatter RUN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReportTemplateService templateService;
    private final ReportExecutionService executionService;
    private final ReportMailComposer reportMailComposer;
    private final TemplateRenderer templateRenderer;
    private final EmailRecipientValidationService emailRecipientValidationService;
    private final NotificationPolicyService notificationPolicyService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final RequestUserContext requestUserContext;

    public ReportMailService(ReportTemplateService templateService,
                             ReportExecutionService executionService,
                             ReportMailComposer reportMailComposer,
                             TemplateRenderer templateRenderer,
                             EmailRecipientValidationService emailRecipientValidationService,
                             NotificationPolicyService notificationPolicyService,
                             NotificationOrchestrator notificationOrchestrator,
                             RequestUserContext requestUserContext) {
        this.templateService = templateService;
        this.executionService = executionService;
        this.reportMailComposer = reportMailComposer;
        this.templateRenderer = templateRenderer;
        this.emailRecipientValidationService = emailRecipientValidationService;
        this.notificationPolicyService = notificationPolicyService;
        this.notificationOrchestrator = notificationOrchestrator;
        this.requestUserContext = requestUserContext;
    }

    @Transactional
    public ReportExecutionModels.MailResponse enqueue(ReportExecutionModels.MailRequest request) {
        if (request == null || request.getTemplateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "templateId is required");
        }
        if (request.getDeliveryMode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryMode is required");
        }

        notificationPolicyService.resolve(NotificationEventCode.REPORT_SHARED_EMAIL)
                .filter(policy -> policy.isEnabled())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Report mail is not enabled."));

        ReportTemplateResponse template = templateService.getById(request.getTemplateId());
        DynamicReportRequest templateRequest = template.getRequest();
        ReportMailConfig mailConfig = templateRequest != null ? templateRequest.getMail() : null;
        if (mailConfig == null || !mailConfig.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report mail is not enabled for this template.");
        }

        validateMode(mailConfig, request.getDeliveryMode());
        String actor = requestUserContext.getCurrentUser().map(user -> user.userId()).orElse("SYSTEM");

        ReportExecutionModels.RunRequest runRequest = new ReportExecutionModels.RunRequest();
        runRequest.setTemplateId(request.getTemplateId());
        runRequest.setFilters(request.getFilters());
        ReportExecutionModels.RunResponse report = executionService.runAll(runRequest, 500);

        Map<String, Object> renderContext = buildRenderContext(template.getName(), actor, request.getFilterSummary());
        ReportExecutionModels.MailFields fields = request.getFields() != null ? request.getFields() : new ReportExecutionModels.MailFields();

        NotificationEvent event = new NotificationEvent();
        event.setEventType(NotificationEventType.REPORT_MAIL);
        event.setEventCode(NotificationEventCode.REPORT_SHARED_EMAIL);
        event.setReferenceType(NotificationReferenceType.REPORT);
        event.setReferenceId(String.valueOf(request.getTemplateId()));
        NotificationExplicitRecipients explicitRecipients = resolveRecipients(mailConfig, fields, renderContext);
        event.setExplicitRecipients(explicitRecipients);
        event.setContext(buildNotificationContext(renderContext, report.rowCount()));

        NotificationContent content = new NotificationContent();
        content.setSubjectTemplate(mergeTextField(mailConfig.getSubject(), fields.getSubject(), renderContext, " "));
        content.setHtml(true);

        try {
            if (request.getDeliveryMode() == ReportExecutionModels.ReportMailDeliveryMode.ATTACHMENT) {
                event.setAttachment(reportMailComposer.createAttachment(template.getName(), report, mailConfig.getAttachmentFormat()));
                content.setBodyTemplate(buildAttachmentBody(
                        mergeTextField(mailConfig.getBody(), fields.getBody(), renderContext, "\n\n"),
                        mergeTextField(mailConfig.getDisclaimer(), fields.getDisclaimer(), renderContext, "\n\n")
                ));
            } else {
                content.setBodyTemplate(reportMailComposer.buildInlineHtml(
                        mergeTextField(mailConfig.getBody(), fields.getBody(), renderContext, "\n\n"),
                        report,
                        mergeTextField(mailConfig.getDisclaimer(), fields.getDisclaimer(), renderContext, "\n\n")
                ));
            }
        } catch (Exception attachmentError) {
            if (mailConfig.getMode() == ReportMailMode.ATTACHMENT_ONLY) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate report attachment.", attachmentError);
            }
            content.setBodyTemplate(reportMailComposer.buildInlineHtml(
                    mergeTextField(mailConfig.getBody(), fields.getBody(), renderContext, "\n\n"),
                    report,
                    mergeTextField(mailConfig.getDisclaimer(), fields.getDisclaimer(), renderContext, "\n\n")
            ));
            event.getContext().put("attachmentFallback", "INLINE");
        }

        event.setContentOverride(content);
        validateForQueue(explicitRecipients, content.getSubjectTemplate(), content.getBodyTemplate(), event.getAttachment());
        if (notificationOrchestrator.publish(event).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to enqueue report mail.");
        }

        return new ReportExecutionModels.MailResponse("QUEUED", "Report email queued.");
    }

    private void validateMode(ReportMailConfig config, ReportExecutionModels.ReportMailDeliveryMode deliveryMode) {
        if (config.getMode() == ReportMailMode.INLINE_ONLY
                && deliveryMode != ReportExecutionModels.ReportMailDeliveryMode.INLINE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This report supports inline email only.");
        }
        if (config.getMode() == ReportMailMode.ATTACHMENT_ONLY
                && deliveryMode != ReportExecutionModels.ReportMailDeliveryMode.ATTACHMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This report supports attachment email only.");
        }
    }

    private NotificationExplicitRecipients resolveRecipients(ReportMailConfig config,
                                                             ReportExecutionModels.MailFields fields,
                                                             Map<String, Object> renderContext) {
        NotificationExplicitRecipients recipients = new NotificationExplicitRecipients();
        recipients.setTo(mergeRecipientField(config.getTo(), fields.getTo(), renderContext));
        recipients.setCc(mergeRecipientField(config.getCc(), fields.getCc(), renderContext));
        return recipients;
    }

    private List<String> mergeRecipientField(ReportMailFieldConfig config,
                                             String requestedValue,
                                             Map<String, Object> renderContext) {
        if (config == null) {
            return splitAddresses(requestedValue);
        }
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        String editableValue = config.isEditable() ? requestedValue : config.getDefaultValue();
        resolved.addAll(splitAddresses(templateRenderer.render(blankSafe(editableValue), renderContext)));
        resolved.addAll(splitAddresses(templateRenderer.render(blankSafe(config.getMandatory()), renderContext)));
        return List.copyOf(resolved);
    }

    private String mergeTextField(ReportMailFieldConfig config,
                                  String requestedValue,
                                  Map<String, Object> renderContext,
                                  String separator) {
        if (config == null) {
            return templateRenderer.render(blankSafe(requestedValue), renderContext).trim();
        }
        String selected = config.isEditable() ? requestedValue : config.getDefaultValue();
        String base = templateRenderer.render(blankSafe(selected), renderContext).trim();
        String mandatory = templateRenderer.render(blankSafe(config.getMandatory()), renderContext).trim();
        if (!StringUtils.hasText(mandatory)) {
            return base;
        }
        if (!StringUtils.hasText(base)) {
            return mandatory;
        }
        if (base.contains(mandatory)) {
            return base;
        }
        return base + separator + mandatory;
    }

    private Map<String, Object> buildRenderContext(String reportName, String actor, String filterSummary) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reportName", reportName);
        context.put("runDate", RUN_DATE_FORMAT.format(OffsetDateTime.now()));
        context.put("triggeredBy", actor);
        context.put("filterSummary", StringUtils.hasText(filterSummary) ? filterSummary : "No filters applied");
        return context;
    }

    private Map<String, Object> buildNotificationContext(Map<String, Object> renderContext, long rowCount) {
        Map<String, Object> context = new LinkedHashMap<>(renderContext);
        context.put("rowCount", rowCount);
        return context;
    }

    private String buildAttachmentBody(String introBody, String disclaimer) {
        StringBuilder body = new StringBuilder();
        if (StringUtils.hasText(introBody)) {
            body.append(introBody.trim());
        }
        if (StringUtils.hasText(disclaimer)) {
            if (body.length() > 0) {
                body.append("<br/><br/>");
            }
            body.append(disclaimer.trim());
        }
        if (body.length() == 0) {
            body.append("Please find the report attached.");
        }
        return body.toString();
    }

    private List<String> splitAddresses(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> addresses = new ArrayList<>();
        for (String token : value.split("[,;\\n]+")) {
            String normalized = token != null ? token.trim().toLowerCase(Locale.ROOT) : null;
            if (StringUtils.hasText(normalized)) {
                addresses.add(normalized);
            }
        }
        return addresses;
    }

    private String blankSafe(String value) {
        return value != null ? value : "";
    }

    private void validateForQueue(NotificationExplicitRecipients recipients,
                                  String subject,
                                  String body,
                                  com.docflow.notification.model.NotificationAttachment attachment) {
        NotificationMessage message = new NotificationMessage();
        message.setTo(recipients.getTo());
        message.setCc(recipients.getCc());
        message.setSubject(subject);
        message.setBody(body);
        message.setHtml(true);
        message.setAttachment(attachment);
        try {
            emailRecipientValidationService.validateAndNormalize(message);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
