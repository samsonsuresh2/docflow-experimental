package com.docflow.notification.email;

import com.docflow.notification.config.NotificationProperties;
import com.docflow.notification.model.NotificationAttachment;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

abstract class AbstractSmtpEmailDeliveryAdapter implements EmailDeliveryAdapter {

    private final NotificationProperties properties;

    protected AbstractSmtpEmailDeliveryAdapter(NotificationProperties properties) {
        this.properties = properties;
    }

    protected NotificationDispatchResult sendVia(NotificationMessage message,
                                                 NotificationProperties.Smtp smtpProperties,
                                                 String adapterName) {
        if (!StringUtils.hasText(smtpProperties.getHost()) || smtpProperties.getPort() <= 0) {
            return NotificationDispatchResult.failure(adapterName, "SMTP host/port is not configured.");
        }

        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(smtpProperties.getHost());
            sender.setPort(smtpProperties.getPort());
            sender.setUsername(smtpProperties.getUsername());
            sender.setPassword(smtpProperties.getPassword());

            Properties sessionProperties = sender.getJavaMailProperties();
            sessionProperties.put("mail.smtp.auth", String.valueOf(smtpProperties.isAuth()));
            sessionProperties.put("mail.smtp.starttls.enable", String.valueOf(smtpProperties.isStartTls()));
            sessionProperties.put("mail.smtp.connectiontimeout", String.valueOf(properties.getMail().getConnectionTimeoutMs()));
            sessionProperties.put("mail.smtp.timeout", String.valueOf(properties.getMail().getReadTimeoutMs()));
            sessionProperties.put("mail.smtp.writetimeout", String.valueOf(properties.getMail().getWriteTimeoutMs()));

            MimeMessage mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, message.getAttachment() != null, StandardCharsets.UTF_8.name());
            helper.setTo(message.getTo().toArray(String[]::new));
            if (!message.getCc().isEmpty()) {
                helper.setCc(message.getCc().toArray(String[]::new));
            }
            helper.setSubject(StringUtils.hasText(message.getSubject()) ? message.getSubject() : "(no subject)");
            helper.setText(message.getBody() != null ? message.getBody() : "", message.isHtml());
            if (StringUtils.hasText(properties.getMail().getFromAddress())) {
                helper.setFrom(properties.getMail().getFromAddress());
            }
            if (StringUtils.hasText(properties.getMail().getReplyTo())) {
                helper.setReplyTo(properties.getMail().getReplyTo());
            }
            attach(message.getAttachment(), helper);

            sender.send(mimeMessage);
            return NotificationDispatchResult.success(adapterName);
        } catch (Exception ex) {
            return NotificationDispatchResult.failure(adapterName, ex.getMessage());
        }
    }

    private void attach(NotificationAttachment attachment, MimeMessageHelper helper) throws MessagingException {
        if (attachment == null) {
            return;
        }
        File file = new File(attachment.getPath());
        if (!file.isFile()) {
            throw new MessagingException("Attachment file not found: " + attachment.getPath());
        }
        String attachmentName = StringUtils.hasText(attachment.getFileName()) ? attachment.getFileName() : file.getName();
        helper.addAttachment(attachmentName, new FileSystemResource(file), attachment.getContentType());
    }
}
