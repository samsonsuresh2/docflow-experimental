package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportTemplateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTemplateServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;
    private ReportTemplateService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ReportTemplateService(jdbcTemplate, objectMapper);
    }

    @Test
    void createTemplatePersistsFiltersInPayload() throws Exception {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.ID"));
        ReportFilter filter = new ReportFilter();
        filter.setKey("DOCUMENT_PARENT.STATUS");
        filter.setOp("=");
        filter.setMode(ReportFilter.Mode.USER_INPUT);
        filter.setValue("");
        request.setFilters(List.of(filter));

        ReportTemplateResponse response = new ReportTemplateResponse(1L, "Status Template", null, request, "admin1", Instant.now());

        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(2);
            keyHolder.getKeyList().add(Map.of("ID", 1L));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));

        when(jdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class))).thenReturn(response);

        service.createTemplate("Status Template", request, "admin1");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture(), any(KeyHolder.class), any(String[].class));

        String json = (String) paramsCaptor.getValue().getValue("configJson");
        JsonNode payload = objectMapper.readTree(json);
        JsonNode filters = payload.path("request").path("filters");
        assertThat(filters.isArray()).isTrue();
        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).path("key").asText()).isEqualTo("DOCUMENT_PARENT.STATUS");
        assertThat(filters.get(0).path("mode").asText()).isEqualTo("USER_INPUT");
    }

    @Test
    void createTemplatePersistsRangeBoundsInPayload() throws Exception {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.ID"));
        ReportFilter filter = new ReportFilter();
        filter.setKey("DOCUMENT_PARENT.CREATED_AT");
        filter.setOp("BETWEEN");
        filter.setMode(ReportFilter.Mode.FIXED_VALUE);
        filter.setLogicalType(ReportFilter.FilterLogicalType.DATE);
        filter.setValueFrom("2026-03-01");
        filter.setValueTo("2026-03-31");
        request.setFilters(List.of(filter));

        ReportTemplateResponse response = new ReportTemplateResponse(1L, "Date Template", null, request, "admin1", Instant.now());

        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(2);
            keyHolder.getKeyList().add(Map.of("ID", 1L));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));

        when(jdbcTemplate.queryForObject(anyString(), anyMap(), any(RowMapper.class))).thenReturn(response);

        service.createTemplate("Date Template", request, "admin1");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture(), any(KeyHolder.class), any(String[].class));

        String json = (String) paramsCaptor.getValue().getValue("configJson");
        JsonNode filters = objectMapper.readTree(json).path("request").path("filters");
        assertThat(filters.get(0).path("op").asText()).isEqualTo("BETWEEN");
        assertThat(filters.get(0).path("valueFrom").asText()).isEqualTo("2026-03-01");
        assertThat(filters.get(0).path("valueTo").asText()).isEqualTo("2026-03-31");
    }
}
