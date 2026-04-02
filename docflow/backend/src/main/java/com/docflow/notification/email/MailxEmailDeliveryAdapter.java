package com.docflow.notification.email;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MailxEmailDeliveryAdapter implements EmailDeliveryAdapter {

    private final NotificationProperties properties;

    public MailxEmailDeliveryAdapter(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getAdapterName() {
        return "MAILX";
    }

    @Override
    public NotificationDispatchResult send(NotificationMessage message) {
        if (!StringUtils.hasText(properties.getMail().getMailxCommandPath())) {
            return NotificationDispatchResult.failure(getAdapterName(), "mailx command path is not configured.");
        }
        if (message.getTo().isEmpty()) {
            return NotificationDispatchResult.failure(getAdapterName(), "At least one To recipient is required.");
        }

        List<String> command = new ArrayList<>();
        command.add(properties.getMail().getMailxCommandPath());
        command.add("-s");
        command.add(StringUtils.hasText(message.getSubject()) ? message.getSubject() : "(no subject)");
        for (String cc : message.getCc()) {
            command.add("-c");
            command.add(cc);
        }
        attach(command, message.getAttachment());
        command.addAll(message.getTo());

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (var output = process.getOutputStream()) {
                output.write((message.getBody() != null ? message.getBody() : "").getBytes(StandardCharsets.UTF_8));
            }
            boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            String processOutput;
            try (InputStream inputStream = process.getInputStream()) {
                processOutput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!completed) {
                process.destroyForcibly();
                return NotificationDispatchResult.failure(getAdapterName(), "mailx timed out.");
            }
            if (process.exitValue() != 0) {
                return NotificationDispatchResult.failure(getAdapterName(),
                        StringUtils.hasText(processOutput) ? processOutput : "mailx failed with exit code " + process.exitValue());
            }
            return NotificationDispatchResult.success(getAdapterName());
        } catch (Exception ex) {
            return NotificationDispatchResult.failure(getAdapterName(), ex.getMessage());
        }
    }

    private void attach(List<String> command, NotificationAttachment attachment) {
        if (attachment == null || !StringUtils.hasText(attachment.getPath())) {
            return;
        }
        command.add("-a");
        command.add(attachment.getPath());
    }
}
