package com.docflow.notification.email;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class MailxEmailDeliveryAdapter implements EmailDeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(MailxEmailDeliveryAdapter.class);
    private static final Pattern TABLE_CELL_CLOSE = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern TABLE_ROW_CLOSE = Pattern.compile("(?i)</tr>");
    private static final Pattern BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_CLOSE = Pattern.compile("(?i)</(p|div|table|thead|tbody|ul|ol|li|h[1-6])>");
    private static final Pattern ANY_TAG = Pattern.compile("(?is)<[^>]+>");

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
        command.add("-r");
        command.add(properties.getMail().getFromAddress());
        command.add("-s");
        command.add(StringUtils.hasText(message.getSubject()) ? message.getSubject() : "(no subject)");
        for (String cc : message.getCc()) {
            command.add("-c");
            command.add(cc);
        }
        attach(command, message.getAttachment());
        command.addAll(message.getTo());
        String body = renderMailxBody(message);

        try {
            log.info(
                    "Executing mailx command: {} | body via stdin | subject={} | to={} | cc={} | attachment={} | bodyLength={} | bodyPreview={}",
                    renderCommand(command),
                    StringUtils.hasText(message.getSubject()) ? message.getSubject() : "(no subject)",
                    message.getTo(),
                    message.getCc(),
                    message.getAttachment() != null ? message.getAttachment().getPath() : "(none)",
                    body.length(),
                    previewBody(body)
            );
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (var output = process.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            boolean completed = process.waitFor(Duration.ofSeconds(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            String processOutput;
            try (InputStream inputStream = process.getInputStream()) {
                processOutput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (!completed) {
                process.destroyForcibly();
                log.warn("mailx timed out after 30s. command={} to={} cc={}", renderCommand(command), message.getTo(), message.getCc());
                return NotificationDispatchResult.failure(getAdapterName(), "mailx timed out.");
            }
            if (process.exitValue() != 0) {
                log.warn(
                        "mailx failed. exitCode={} command={} output={}",
                        process.exitValue(),
                        renderCommand(command),
                        StringUtils.hasText(processOutput) ? processOutput : "(no output)"
                );
                return NotificationDispatchResult.failure(getAdapterName(),
                        StringUtils.hasText(processOutput) ? processOutput : "mailx failed with exit code " + process.exitValue());
            }
            log.info("mailx completed successfully. command={} to={} cc={}", renderCommand(command), message.getTo(), message.getCc());
            return NotificationDispatchResult.success(getAdapterName());
        } catch (Exception ex) {
            log.error("mailx execution threw an exception. command={} message={}", renderCommand(command), ex.getMessage(), ex);
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

    private String renderCommand(List<String> command) {
        return command.stream()
                .map(this::quoteArgument)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String quoteArgument(String arg) {
        if (arg == null) {
            return "\"\"";
        }
        String escaped = arg.replace("\\", "\\\\").replace("\"", "\\\"");
        if (escaped.isEmpty() || escaped.chars().anyMatch(Character::isWhitespace)) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String previewBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "(empty)";
        }
        String normalized = body
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return normalized.length() > 200 ? normalized.substring(0, 200) + "..." : normalized;
    }

    private String renderMailxBody(NotificationMessage message) {
        String body = message.getBody() != null ? message.getBody() : "";
        if (!message.isHtml() || !StringUtils.hasText(body)) {
            return body;
        }

        String normalized = TABLE_CELL_CLOSE.matcher(body).replaceAll("\t");
        normalized = TABLE_ROW_CLOSE.matcher(normalized).replaceAll(System.lineSeparator());
        normalized = BREAK_TAG.matcher(normalized).replaceAll(System.lineSeparator());
        normalized = BLOCK_CLOSE.matcher(normalized).replaceAll(System.lineSeparator());
        normalized = ANY_TAG.matcher(normalized).replaceAll("");
        normalized = normalized
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        normalized = normalized.replaceAll("[\\t ]+" + System.lineSeparator(), System.lineSeparator());
        normalized = normalized.replaceAll(System.lineSeparator() + "{3,}", System.lineSeparator() + System.lineSeparator());
        return normalized.trim();
    }
}
