package com.docflow.notification.email;

import com.docflow.notification.model.NotificationDeliveryStatus;
import com.docflow.notification.model.NotificationDispatchResult;
import com.docflow.notification.model.NotificationMessage;
import com.docflow.notification.service.EmailRecipientValidationService;
import com.docflow.notification.service.NotificationAdapterSelector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationChannelTest {

    @Test
    void fallsBackToNextAdapterWhenPrimaryFails() {
        NotificationAdapterSelector selector = mock(NotificationAdapterSelector.class);
        EmailRecipientValidationService validationService = mock(EmailRecipientValidationService.class);
        EmailDeliveryAdapter smtp = mock(EmailDeliveryAdapter.class);
        EmailDeliveryAdapter localhost = mock(EmailDeliveryAdapter.class);

        NotificationMessage message = new NotificationMessage();
        message.setTo(List.of("maker@company.internal"));

        when(selector.adapterPriority(any())).thenReturn(List.of("SMTP", "LOCALHOST_SMTP"));
        when(validationService.validateAndNormalize(any(NotificationMessage.class))).thenReturn(message);
        when(smtp.getAdapterName()).thenReturn("SMTP");
        when(localhost.getAdapterName()).thenReturn("LOCALHOST_SMTP");
        when(smtp.send(message)).thenReturn(NotificationDispatchResult.failure("SMTP", "relay down"));
        when(localhost.send(message)).thenReturn(NotificationDispatchResult.success("LOCALHOST_SMTP"));

        EmailNotificationChannel channel = new EmailNotificationChannel(selector, validationService, List.of(smtp, localhost));

        NotificationDispatchResult result = channel.send(message);

        assertThat(result.status()).isEqualTo(NotificationDeliveryStatus.SUCCESS);
        assertThat(result.adapterName()).isEqualTo("LOCALHOST_SMTP");
        verify(smtp).send(message);
        verify(localhost).send(message);
    }

    @Test
    void returnsValidationFailureWithoutCallingAdapters() {
        NotificationAdapterSelector selector = mock(NotificationAdapterSelector.class);
        EmailRecipientValidationService validationService = mock(EmailRecipientValidationService.class);
        EmailDeliveryAdapter smtp = mock(EmailDeliveryAdapter.class);

        NotificationMessage message = new NotificationMessage();
        when(validationService.validateAndNormalize(any(NotificationMessage.class)))
                .thenThrow(new IllegalArgumentException("At least one To recipient is required."));
        when(smtp.getAdapterName()).thenReturn("SMTP");

        EmailNotificationChannel channel = new EmailNotificationChannel(selector, validationService, List.of(smtp));

        NotificationDispatchResult result = channel.send(message);

        assertThat(result.status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(result.adapterName()).isEqualTo("VALIDATION");
        assertThat(result.errorMessage()).contains("At least one To recipient is required");
    }
}
