package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.service.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportFilterTypeValidationServiceTest {

    private ReportMetadataService metadataService;
    private ConfigService configService;
    private ReportFilterTypeValidationService service;

    @BeforeEach
    void setUp() {
        metadataService = mock(ReportMetadataService.class);
        configService = mock(ConfigService.class);
        service = new ReportFilterTypeValidationService(metadataService, properties(), configService, new ObjectMapper());

        when(metadataService.getColumnDataType("DOCUMENT_PARENT", "STATUS")).thenReturn("VARCHAR2");
        when(metadataService.getColumnDataType("DOCUMENT_PARENT", "CREATED_AT")).thenReturn("TIMESTAMP");
        when(metadataService.getColumnDataType("LOAN_DATA", "LOAN_AMOUNT")).thenReturn("NUMBER");
        when(metadataService.getColumnDataType("DOCUMENT_METADATA", "FIELD_VALUE")).thenReturn("CLOB");
    }

    @Test
    void validTypeMappingSaveAllowsMatchingDocumentColumn() {
        DynamicReportRequest request = request("DOCUMENT_PARENT", filter("STATUS", ReportFilter.FilterLogicalType.STRING));

        assertDoesNotThrow(() -> service.validateTemplateDefinition(request));
    }

    @Test
    void invalidTypeMappingSaveBlockedForDocumentColumn() {
        DynamicReportRequest request = request("DOCUMENT_PARENT", filter("STATUS", ReportFilter.FilterLogicalType.NUMBER));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.validateTemplateDefinition(request));

        assertEquals(400, exception.getStatusCode().value());
        org.assertj.core.api.Assertions.assertThat(exception.getReason())
                .contains("STATUS")
                .contains("DOCUMENT_PARENT.STATUS")
                .contains("VARCHAR2")
                .contains("STRING");
    }

    @Test
    void metadataBackedFieldValidationUsesConfiguredMetadataType() {
        when(configService.getUploadFieldsConfig()).thenReturn("""
                {
                  "fields": [
                    { "key": "applicationDate", "type": "date" }
                  ]
                }
                """);

        DynamicReportRequest request = request("DOCUMENT_PARENT", filter("meta:applicationDate", ReportFilter.FilterLogicalType.DATE));

        assertDoesNotThrow(() -> service.validateTemplateDefinition(request));
    }

    @Test
    void metadataBackedFieldValidationBlocksMismatchedConfiguredType() {
        when(configService.getUploadFieldsConfig()).thenReturn("""
                {
                  "fields": [
                    { "key": "applicationDate", "type": "date" }
                  ]
                }
                """);

        DynamicReportRequest request = request("DOCUMENT_PARENT", filter("meta:applicationDate", ReportFilter.FilterLogicalType.NUMBER));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.validateTemplateDefinition(request));

        assertEquals(400, exception.getStatusCode().value());
        org.assertj.core.api.Assertions.assertThat(exception.getReason())
                .contains("meta:applicationDate")
                .contains("metadata field 'applicationDate'")
                .contains("DATE");
    }

    @Test
    void thirdEntityTableFieldValidationSupportsNumericColumns() {
        DynamicReportRequest request = request("LOAN_DATA", filter("LOAN_AMOUNT", ReportFilter.FilterLogicalType.NUMBER));

        assertDoesNotThrow(() -> service.validateTemplateDefinition(request));
    }

    private DynamicReportRequest request(String baseEntity, ReportFilter filter) {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity(baseEntity);
        request.setColumns(List.of("DOCUMENT_PARENT.DOCUMENT_NUMBER"));
        request.setFilters(List.of(filter));
        return request;
    }

    private ReportFilter filter(String key, ReportFilter.FilterLogicalType type) {
        ReportFilter filter = new ReportFilter();
        filter.setKey(key);
        filter.setOp("EQ");
        filter.setMode(ReportFilter.Mode.USER_INPUT);
        filter.setLogicalType(type);
        filter.setDataType(type.name());
        return filter;
    }

    private ReportProperties properties() {
        ReportProperties properties = new ReportProperties();

        ReportProperties.DocumentTableProperties document = new ReportProperties.DocumentTableProperties();
        document.setName("DOCUMENT_PARENT");
        document.setInternalPk("ID");
        document.setBusinessKey("DOCUMENT_NUMBER");
        properties.setDocumentTable(document);

        ReportProperties.MetadataTableProperties metadata = new ReportProperties.MetadataTableProperties();
        metadata.setName("DOCUMENT_METADATA");
        metadata.setDocumentIdColumn("DOCUMENT_ID");
        metadata.setKeyColumn("FIELD_KEY");
        metadata.setValueColumn("FIELD_VALUE");
        properties.setMetadataTable(metadata);

        ReportProperties.EntityProperties loanData = new ReportProperties.EntityProperties();
        loanData.setName("LOAN_DATA");
        ReportProperties.JoinProperties join = new ReportProperties.JoinProperties();
        join.setEnabled(true);
        join.setBusinessFkColumn("USER_ID");
        loanData.setJoinToDocument(join);
        properties.setEntities(List.of(loanData));

        return properties;
    }
}
