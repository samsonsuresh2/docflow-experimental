package com.docflow.notification.service;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailRecipientValidationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void validatesAllowedDomainsAndDeduplicatesRecipients() throws Exception {
        NotificationProperties properties = properties();
        EmailRecipientValidationService service = new EmailRecipientValidationService(properties);

        NotificationMessage message = new NotificationMessage();
        message.setTo(List.of("maker@company.internal", "MAKER@company.internal"));
        message.setCc(List.of("maker@company.internal", "reviewer@company.internal"));

        NotificationAttachment attachment = new NotificationAttachment();
        Path file = Files.writeString(tempDir.resolve("report.csv"), "a,b");
        attachment.setPath(file.toString());
        message.setAttachment(attachment);

        NotificationMessage normalized = service.validateAndNormalize(message);

        assertThat(normalized.getTo()).containsExactly("maker@company.internal");
        assertThat(normalized.getCc()).containsExactly("reviewer@company.internal");
    }

    @Test
    void splitsDelimitedRecipientListsBeforeValidation() {
        EmailRecipientValidationService service = new EmailRecipientValidationService(properties());

        NotificationMessage message = new NotificationMessage();
        message.setTo(List.of("teama@company.internal, teamb@company.internal"));
        message.setCc(List.of("reviewer1@company.internal;reviewer2@company.internal\nreviewer3@company.internal"));

        NotificationMessage normalized = service.validateAndNormalize(message);

        assertThat(normalized.getTo()).containsExactly(
                "teama@company.internal",
                "teamb@company.internal"
        );
        assertThat(normalized.getCc()).containsExactly(
                "reviewer1@company.internal",
                "reviewer2@company.internal",
                "reviewer3@company.internal"
        );
    }

    @Test
    void rejectsRecipientsOutsideAllowedDomains() {
        EmailRecipientValidationService service = new EmailRecipientValidationService(properties());

        NotificationMessage message = new NotificationMessage();
        message.setTo(List.of("maker@example.com"));

        assertThatThrownBy(() -> service.validateAndNormalize(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domain is not allowed");
    }

    @Test
    void rejectsRecipientCountOverConfiguredLimits() {
        NotificationProperties properties = properties();
        properties.getMail().setMaxToCount(1);
        properties.getMail().setMaxCcCount(0);
        EmailRecipientValidationService service = new EmailRecipientValidationService(properties);

        NotificationMessage message = new NotificationMessage();
        message.setTo(List.of("a@company.internal", "b@company.internal"));
        message.setCc(List.of("c@company.internal"));

        assertThatThrownBy(() -> service.validateAndNormalize(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("To recipient count exceeds");
    }

    @Test
    void rejectsMissingAttachmentFile() {
        EmailRecipientValidationService service = new EmailRecipientValidationService(properties());

        NotificationMessage message = new NotificationMessage();
        message.setTo(List.of("maker@company.internal"));
        NotificationAttachment attachment = new NotificationAttachment();
        attachment.setPath(tempDir.resolve("missing.csv").toString());
        message.setAttachment(attachment);

        assertThatThrownBy(() -> service.validateAndNormalize(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attachment file does not exist");
    }

    private NotificationProperties properties() {
        NotificationProperties properties = new NotificationProperties();
        properties.getMail().setAllowedInternalDomains(List.of("company.internal"));
        properties.getMail().setMaxToCount(10);
        properties.getMail().setMaxCcCount(10);
        return properties;
    }
}
