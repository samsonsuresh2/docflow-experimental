package com.docflow.notification.render;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void replacesKnownPlaceholdersAndBlanksMissingValues() {
        String rendered = renderer.render(
                "Report ${reportName} was triggered by ${triggeredBy} for ${missingValue}.",
                Map.of("reportName", "Loan Summary", "triggeredBy", "maker1")
        );

        assertThat(rendered).isEqualTo("Report Loan Summary was triggered by maker1 for .");
    }

    @Test
    void returnsEmptyStringForNullTemplate() {
        assertThat(renderer.render(null, Map.of("key", "value"))).isEmpty();
    }
}
