package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatePresetServiceTest {

    private DatePresetService service;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ReportProperties props = new ReportProperties();
        props.setWeekStartDay("MONDAY");
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        service = new DatePresetService(jdbcTemplate, props);
    }

    @Test
    void shouldResolveWeekAndMonthRules() {
        LocalDate today = LocalDate.of(2026, 3, 6); // Friday
        assertEquals(LocalDate.of(2026, 3, 2), service.resolveRule("CURRENT_WEEK_START", today));
        assertEquals(LocalDate.of(2026, 3, 1), service.resolveRule("PREVIOUS_WEEK_END", today));
        assertEquals(LocalDate.of(2026, 3, 1), service.resolveRule("CURRENT_MONTH_START", today));
        assertEquals(LocalDate.of(2026, 2, 1), service.resolveRule("PREVIOUS_MONTH_START", today));
    }

    @Test
    void shouldResolveAnchoredWeekdayAndOffsetRules() {
        LocalDate today = LocalDate.of(2026, 3, 6); // Friday
        assertEquals(LocalDate.of(2026, 3, 4), service.resolveRule("CURRENT_WEDNESDAY", today));
        assertEquals(LocalDate.of(2026, 2, 25), service.resolveRule("PREVIOUS_WEDNESDAY", today));
        assertEquals(LocalDate.of(2026, 2, 26), service.resolveRule("PREVIOUS_WEDNESDAY+1", today));
    }

    @Test
    void shouldHandleLeapYearBoundary() {
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        assertEquals(LocalDate.of(2024, 2, 1), service.resolveRule("CURRENT_MONTH_START", leapDay));
        assertEquals(LocalDate.of(2024, 2, 29), service.resolveRule("CURRENT_MONTH_END", leapDay));
        assertEquals(LocalDate.of(2024, 1, 31), service.resolveRule("PREVIOUS_MONTH_END", leapDay));
    }

    @Test
    void shouldRejectUnmappedPresetCode() {
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> service.resolvePreset("r1", "CREATED_AT", "THIS_WEEK"));
    }
}
