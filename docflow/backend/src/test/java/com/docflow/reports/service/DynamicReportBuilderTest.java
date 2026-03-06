package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DynamicReportBuilderTest {

    private ReportMetadataService metadataService;
    private DynamicReportBuilder builder;

    @BeforeEach
    void setUp() {
        metadataService = mock(ReportMetadataService.class);
        ReportProperties properties = new ReportProperties();
        ReportProperties.DocumentTableProperties doc = new ReportProperties.DocumentTableProperties();
        doc.setName("DOCUMENT_PARENT");
        doc.setInternalPk("ID");
        doc.setBusinessKey("DOCUMENT_NUMBER");
        properties.setDocumentTable(doc);

        ReportProperties.MetadataTableProperties meta = new ReportProperties.MetadataTableProperties();
        meta.setName("DOCUMENT_METADATA");
        meta.setDocumentIdColumn("DOCUMENT_ID");
        meta.setKeyColumn("FIELD_KEY");
        meta.setValueColumn("FIELD_VALUE");
        meta.setValueIsClob(true);
        properties.setMetadataTable(meta);

        ReportProperties.EntityProperties loan = new ReportProperties.EntityProperties();
        loan.setName("LOAN_DATA");
        ReportProperties.JoinProperties join = new ReportProperties.JoinProperties();
        join.setEnabled(true);
        join.setBusinessFkColumn("USER_ID");
        loan.setJoinToDocument(join);
        properties.setEntities(List.of(loan));

        when(metadataService.getColumns("DOCUMENT_PARENT"))
                .thenReturn(new ReportMetadataService.EntityColumns("DOCUMENT_PARENT", List.of("ID", "DOCUMENT_NUMBER", "AMOUNT", "CREATED_DATE")));
        when(metadataService.getColumns("LOAN_DATA"))
                .thenReturn(new ReportMetadataService.EntityColumns("LOAN_DATA", List.of("USER_ID", "DUE_AMOUNT")));
        when(metadataService.listMetadataKeys()).thenReturn(List.of("branch", "loanAmount", "applicationDate"));

        builder = new DynamicReportBuilder(metadataService, properties);
    }

    @Test
    void shouldBuildDocumentAndMetadataTypedPredicates() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.DOCUMENT_NUMBER"));
        request.setFilters(List.of(
                filter("DOCUMENT_PARENT.AMOUNT", "GT", "100", "NUMBER"),
                filter("DOCUMENT_PARENT.CREATED_DATE", "LT", "2026-03-01", "DATE"),
                filter("meta:branch", "EQ", "Avadi", "STRING"),
                filter("meta:loanAmount", "GT", "50000", "NUMBER"),
                filter("meta:applicationDate", "LT", "2026-03-06", "DATE")
        ));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertTrue(built.sql().contains("TO_NUMBER(dp.AMOUNT) >"));
        assertTrue(built.sql().contains("TO_DATE(dp.CREATED_DATE, 'YYYY-MM-DD') <"));
        assertTrue(built.sql().contains("LOWER(DBMS_LOB.SUBSTR(dm.FIELD_VALUE"));
        assertTrue(built.sql().contains("TO_NUMBER(DBMS_LOB.SUBSTR(dm.FIELD_VALUE"));
        assertTrue(built.sql().contains("TO_DATE(DBMS_LOB.SUBSTR(dm.FIELD_VALUE"));
    }

    @Test
    void shouldBuildStringLikePredicatesForAllSources() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("LOAN_DATA");
        request.setColumns(List.of("LOAN_DATA.DUE_AMOUNT"));
        request.setFilters(List.of(
                filter("DOCUMENT_PARENT.DOCUMENT_NUMBER", "LIKE", "DOC", "STRING"),
                filter("meta:branch", "LIKE", "avi", "STRING"),
                filter("LOAN_DATA.USER_ID", "LIKE", "usr", "STRING")
        ));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertTrue(built.sql().contains("LOWER(dp.DOCUMENT_NUMBER) LIKE"));
        assertTrue(built.sql().contains("LOWER(DBMS_LOB.SUBSTR(dm.FIELD_VALUE"));
        assertTrue(built.sql().contains("LOWER(b.USER_ID) LIKE"));
        assertTrue(built.parameters().values().contains("%doc%"));
        assertTrue(built.parameters().values().contains("%avi%"));
        assertTrue(built.parameters().values().contains("%usr%"));
    }

    @Test
    void shouldBuildThirdPartyPredicateUsingDynamicMetadataPath() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("LOAN_DATA");
        request.setColumns(List.of("LOAN_DATA.DUE_AMOUNT"));
        request.setFilters(List.of(filter("LOAN_DATA.DUE_AMOUNT", "GT", "1000", "NUMBER")));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertTrue(built.sql().contains("FROM LOAN_DATA b JOIN DOCUMENT_PARENT dp"));
        assertTrue(built.sql().contains("TO_NUMBER(b.DUE_AMOUNT) >"));
    }

    private ReportFilter filter(String key, String op, String value, String dataType) {
        ReportFilter filter = new ReportFilter();
        filter.setKey(key);
        filter.setOp(op);
        filter.setValue(value);
        filter.setDataType(dataType);
        return filter;
    }
}
