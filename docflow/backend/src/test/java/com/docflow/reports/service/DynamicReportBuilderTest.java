package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicReportBuilderTest {

    private ReportMetadataService metadataService;
    private DynamicReportBuilder builder;

    @BeforeEach
    void setUp() {
        metadataService = mock(ReportMetadataService.class);
        builder = new DynamicReportBuilder(metadataService, properties());

        when(metadataService.getColumns("DOCUMENT_PARENT"))
                .thenReturn(new ReportMetadataService.EntityColumns("DOCUMENT_PARENT", List.of("ID", "DOCUMENT_NUMBER", "CREATED_AT", "STATUS")));
        when(metadataService.getColumns("LOAN_DATA"))
                .thenReturn(new ReportMetadataService.EntityColumns("LOAN_DATA", List.of("USER_ID", "LOAN_AMOUNT", "COMPLETION_DATE")));
        when(metadataService.listMetadataKeys()).thenReturn(List.of("applicationDate", "loanAmount"));
    }

    @Test
    void shouldBuildDocumentDateBetweenUsingTruncAndDateBounds() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_NUMBER", "CREATED_AT"));
        request.setFilters(List.of(rangeFilter("CREATED_AT", "BETWEEN", "2026-03-01", "2026-03-31", ReportFilter.FilterLogicalType.DATE)));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("TRUNC(dp.CREATED_AT) BETWEEN TO_DATE(:p0, 'YYYY-MM-DD') AND TO_DATE(:p1, 'YYYY-MM-DD')");
    }

    @Test
    void shouldBuildDocumentDatePresetRangeUsingGreaterAndLessComparisons() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_NUMBER", "CREATED_AT"));
        request.setFilters(List.of(
                valueFilter("CREATED_AT", "GE", "2026-03-24", ReportFilter.FilterLogicalType.DATE),
                valueFilter("CREATED_AT", "LE", "2026-03-24", ReportFilter.FilterLogicalType.DATE)
        ));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("TRUNC(dp.CREATED_AT) >= TO_DATE(:p0, 'YYYY-MM-DD')");
        assertThat(built.sql()).contains("TRUNC(dp.CREATED_AT) <= TO_DATE(:p1, 'YYYY-MM-DD')");
    }

    @Test
    void shouldBuildMetadataDateBetweenWithDateConversion() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_NUMBER"));
        request.setFilters(List.of(rangeFilter("meta:applicationDate", "BETWEEN", "2026-03-01", "2026-03-31", ReportFilter.FilterLogicalType.DATE)));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("TO_DATE(dm.FIELD_VALUE, 'YYYY-MM-DD') BETWEEN TO_DATE(:p1, 'YYYY-MM-DD') AND TO_DATE(:p2, 'YYYY-MM-DD')");
    }

    @Test
    void shouldBuildThirdPartyDateBetweenAgainstBaseColumn() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("LOAN_DATA");
        request.setColumns(List.of("LOAN_AMOUNT", "COMPLETION_DATE"));
        request.setFilters(List.of(rangeFilter("COMPLETION_DATE", "BETWEEN", "2026-03-01", "2026-03-31", ReportFilter.FilterLogicalType.DATE)));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("TRUNC(b.COMPLETION_DATE) BETWEEN TO_DATE(:p0, 'YYYY-MM-DD') AND TO_DATE(:p1, 'YYYY-MM-DD')");
    }

    @Test
    void shouldBuildDocumentNumberRange() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("LOAN_DATA");
        request.setColumns(List.of("LOAN_AMOUNT"));
        request.setFilters(List.of(rangeFilter("LOAN_AMOUNT", "RANGE", "100", "250", ReportFilter.FilterLogicalType.NUMBER)));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("TO_NUMBER(b.LOAN_AMOUNT) BETWEEN TO_NUMBER(:p0) AND TO_NUMBER(:p1)");
    }

    @Test
    void shouldBuildMetadataNumberRangeWithNumericConversion() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_NUMBER"));
        request.setFilters(List.of(rangeFilter("meta:loanAmount", "RANGE", "100", "250", ReportFilter.FilterLogicalType.NUMBER)));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("TO_NUMBER(dm.FIELD_VALUE) BETWEEN TO_NUMBER(:p1) AND TO_NUMBER(:p2)");
    }

    @Test
    void shouldKeepExistingLikeBehavior() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_NUMBER"));
        ReportFilter filter = new ReportFilter();
        filter.setKey("STATUS");
        filter.setOp("LIKE");
        filter.setValue("PEND");
        filter.setLogicalType(ReportFilter.FilterLogicalType.STRING);
        filter.setDataType("STRING");
        request.setFilters(List.of(filter));

        DynamicReportBuilder.BuiltReport built = builder.build(request);

        assertThat(built.sql()).contains("LOWER(dp.STATUS) LIKE :p0");
    }

    private static ReportFilter rangeFilter(String key, String op, String from, String to, ReportFilter.FilterLogicalType type) {
        ReportFilter filter = new ReportFilter();
        filter.setKey(key);
        filter.setOp(op);
        filter.setValueFrom(from);
        filter.setValueTo(to);
        filter.setLogicalType(type);
        filter.setDataType(type.name());
        return filter;
    }

    private static ReportFilter valueFilter(String key, String op, String value, ReportFilter.FilterLogicalType type) {
        ReportFilter filter = new ReportFilter();
        filter.setKey(key);
        filter.setOp(op);
        filter.setValue(value);
        filter.setLogicalType(type);
        filter.setDataType(type.name());
        return filter;
    }

    private static ReportProperties properties() {
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
        metadata.setValueIsClob(false);
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
