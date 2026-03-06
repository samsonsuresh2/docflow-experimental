package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DatePresetServiceTest {

    private DatePresetService service;

    @BeforeEach
    void setUp() {
        ReportProperties props = new ReportProperties();
        props.setWeekStartDay("MONDAY");
        service = new DatePresetService(mock(NamedParameterJdbcTemplate.class), props);
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
}
