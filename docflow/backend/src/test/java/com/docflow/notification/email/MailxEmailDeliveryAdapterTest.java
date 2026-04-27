package com.docflow.notification.email;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationDeliveryStatus;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailxEmailDeliveryAdapterTest {

    @Test
    void sendFailsWhenCommandPathIsMissing() {
        NotificationProperties properties = new NotificationProperties();
        properties.getMail().setMailxCommandPath("");
        MailxEmailDeliveryAdapter adapter = new MailxEmailDeliveryAdapter(properties);

        NotificationDispatchResult result = adapter.send(message(List.of("user@company.internal")));

        assertThat(result.status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(result.adapterName()).isEqualTo("MAILX");
        assertThat(result.errorMessage()).contains("command path");
    }

    @Test
    void sendFailsWhenToRecipientIsMissing() {
        NotificationProperties properties = new NotificationProperties();
        properties.getMail().setMailxCommandPath("mailx");
        MailxEmailDeliveryAdapter adapter = new MailxEmailDeliveryAdapter(properties);

        NotificationDispatchResult result = adapter.send(message(List.of()));

        assertThat(result.status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(result.errorMessage()).contains("To recipient");
    }

    @Test
    void sendReturnsFailureWhenProcessCannotStartAndHtmlBodyRequiresTextRendering() {
        NotificationProperties properties = new NotificationProperties();
        properties.getMail().setMailxCommandPath("definitely-missing-mailx-command");
        properties.getMail().setFromAddress("noreply@company.internal");
        MailxEmailDeliveryAdapter adapter = new MailxEmailDeliveryAdapter(properties);
        NotificationMessage message = message(List.of("user@company.internal"));
        message.setCc(List.of("cc user@company.internal"));
        message.setSubject("");
        message.setHtml(true);
        message.setBody("<table><tr><th>A&amp;B</th></tr><tr><td>&lt;value&gt;</td></tr></table><br/>Done");
        NotificationAttachment attachment = new NotificationAttachment();
        attachment.setPath("C:\\temp\\report file.csv");
        message.setAttachment(attachment);

        NotificationDispatchResult result = adapter.send(message);

        assertThat(result.status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(result.adapterName()).isEqualTo("MAILX");
    }

    private NotificationMessage message(List<String> to) {
        NotificationMessage message = new NotificationMessage();
        message.setTo(to);
        message.setSubject("Subject");
        message.setBody("Body");
        return message;
    }
}
