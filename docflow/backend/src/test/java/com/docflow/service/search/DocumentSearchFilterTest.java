package com.docflow.service.search;

import com.docflow.api.dto.FilterDefinition;
import com.docflow.api.dto.FilterSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSearchFilterTest {

    @Test
    void fromDefinitionParsesOperationsAndNormalizesCandidateValues() {
        FilterDefinition definition = new FilterDefinition();
        definition.setKey("amount");
        definition.setSource(FilterSource.META_DATA);
        definition.setType("number");

        assertThat(DocumentSearchFilter.fromDefinition(definition, " > 100 ").getOperation()).isEqualTo(FilterOperation.GREATER_THAN);
        assertThat(DocumentSearchFilter.fromDefinition(definition, "< 200").getOperation()).isEqualTo(FilterOperation.LESS_THAN);
        assertThat(DocumentSearchFilter.fromDefinition(definition, "= 150").getOperation()).isEqualTo(FilterOperation.EQUALS);
        assertThat(DocumentSearchFilter.fromDefinition(definition, "like:abc").getOperation()).isEqualTo(FilterOperation.LIKE);
        assertThat(DocumentSearchFilter.fromDefinition(definition, "like abc").getRawValue()).isEqualTo("abc");
        assertThat(DocumentSearchFilter.fromDefinition(definition, "a%c").getOperation()).isEqualTo(FilterOperation.LIKE);
        assertThat(DocumentSearchFilter.fromDefinition(definition, List.of("", " 42 ")).getRawValue()).isEqualTo("42");
        assertThat(DocumentSearchFilter.fromDefinition(definition, new Object[] {"", true}).getRawValue()).isEqualTo("true");
    }

    @Test
    void fromDefinitionRejectsIncompleteDefinitionsAndBlankValues() {
        assertThat(DocumentSearchFilter.fromDefinition(null, "x")).isNull();

        FilterDefinition definition = new FilterDefinition();
        definition.setKey(" ");
        definition.setSource(FilterSource.DOCUMENT_PARENT);
        assertThat(DocumentSearchFilter.fromDefinition(definition, "x")).isNull();

        definition.setKey("status");
        definition.setSource(null);
        assertThat(DocumentSearchFilter.fromDefinition(definition, "x")).isNull();

        definition.setSource(FilterSource.DOCUMENT_PARENT);
        assertThat(DocumentSearchFilter.fromDefinition(definition, " ")).isNull();
        assertThat(DocumentSearchFilter.fromDefinition(definition, "like: ")).isNull();
    }

    @Test
    void metadataPlaceholderBuildsComparableFilter() {
        DocumentSearchFilter left = DocumentSearchFilter.metadataPlaceholder("region", "west");
        DocumentSearchFilter right = DocumentSearchFilter.metadataPlaceholder("region", "west");

        assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);
        assertThat(left.toString()).contains("region", "west", "META_DATA");
        assertThat(DocumentSearchFilter.metadataPlaceholder("region", "")).isNull();
    }
}
