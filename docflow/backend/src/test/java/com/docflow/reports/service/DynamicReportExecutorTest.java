package com.docflow.reports.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicReportExecutorTest {

    @Test
    void shouldTranslateTypeMismatchSqlFailuresIntoControlledMessage() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DynamicReportExecutor executor = new DynamicReportExecutor(jdbcTemplate);
        DynamicReportBuilder.BuiltReport report = new DynamicReportBuilder.BuiltReport(
                "SELECT STATUS AS c0 FROM DOCUMENT_PARENT",
                Map.of(),
                List.of(new DynamicReportBuilder.SelectColumn("c0", "STATUS", "dp.STATUS")),
                List.of(),
                "DOCUMENT_METADATA",
                "FIELD_KEY",
                "template:42"
        );

        when(jdbcTemplate.queryForObject(anyString(), anyMap(), org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenThrow(new DataIntegrityViolationException("ORA-01722: invalid number"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> executor.execute(report, 0, 25));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(ReportFilterTypeValidationService.INVALID_FILTER_CONFIGURATION_MESSAGE, exception.getReason());
    }
}
