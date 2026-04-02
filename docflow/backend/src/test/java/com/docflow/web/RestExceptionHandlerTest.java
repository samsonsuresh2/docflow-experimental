package com.docflow.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void shouldSerializeResponseStatusExceptionReasonIntoMessageBody() {
        ResponseStatusException exception = new ResponseStatusException(
            HttpStatus.CONFLICT,
            "This report has an invalid filter configuration. Please contact your administrator."
        );

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
            .containsEntry("status", 409)
            .containsEntry("error", "Conflict")
            .containsEntry("message", "This report has an invalid filter configuration. Please contact your administrator.");
    }

    @Test
    void shouldFallbackToReasonPhraseWhenReasonIsMissing() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .containsEntry("status", 400)
            .containsEntry("error", "Bad Request")
            .containsEntry("message", "Bad Request");
    }
}
