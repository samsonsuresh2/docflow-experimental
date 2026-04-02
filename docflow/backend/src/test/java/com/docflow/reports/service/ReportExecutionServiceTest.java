package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportMailConfig;
import com.docflow.reports.dto.ReportTemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReportExecutionServiceTest {

    private ReportTemplateService templateService;
    private DynamicReportBuilder builder;
    private DynamicReportExecutor executor;
    private DatePresetService datePresetService;
    private ReportExecutionService service;

    @BeforeEach
    void setUp() {
        templateService = mock(ReportTemplateService.class);
        builder = mock(DynamicReportBuilder.class);
        executor = mock(DynamicReportExecutor.class);
        datePresetService = mock(DatePresetService.class);
        service = new ReportExecutionService(templateService, builder, executor, datePresetService);
        when(datePresetService.listPresetsForFilter(any(), any())).thenReturn(List.of());

        DynamicReportBuilder.BuiltReport built = new DynamicReportBuilder.BuiltReport("SELECT 1", Map.of(), List.of(), List.of(), "", "", "ctx");
        when(builder.build(any(DynamicReportRequest.class), eq("template:10"))).thenReturn(built);
        when(executor.execute(any(), eq(0), eq(25))).thenReturn(Map.of("columns", List.of(), "rows", List.of()));
    }

    @Test
    void shouldExposeOperatorApplicabilityByType() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:branch", ReportFilter.FilterLogicalType.STRING));
        ReportExecutionModels.TemplateDetail detail = service.getExecutableTemplate(10L);
        assertEquals(List.of("EQ", "LIKE"), detail.filters().get(0).allowedOps());

        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:loanAmount", ReportFilter.FilterLogicalType.NUMBER));
        detail = service.getExecutableTemplate(10L);
        assertEquals(List.of("EQ", "LT", "GT", "RANGE"), detail.filters().get(0).allowedOps());

        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:applicationDate", ReportFilter.FilterLogicalType.DATE));
        detail = service.getExecutableTemplate(10L);
        assertEquals(List.of("EQ", "LT", "GT", "BETWEEN"), detail.filters().get(0).allowedOps());
    }

    @Test
    void shouldValidateNumberAndDateAndSkipBlank() {
        when(templateService.getById(10L)).thenReturn(templateWithTwoUserFilters());

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        request.setTemplateId(10L);
        ReportExecutionModels.RunFilter number = new ReportExecutionModels.RunFilter();
        number.setKey("meta:loanAmount");
        number.setOp("GT");
        number.setValue("12.50");
        ReportExecutionModels.RunFilter blank = new ReportExecutionModels.RunFilter();
        blank.setKey("meta:applicationDate");
        blank.setOp("EQ");
        blank.setValue("  ");
        request.setFilters(List.of(number, blank));

        service.run(request, 0, 25);

        ArgumentCaptor<DynamicReportRequest> captor = ArgumentCaptor.forClass(DynamicReportRequest.class);
        verify(builder).build(captor.capture(), eq("template:10"));
        List<ReportFilter> applied = captor.getValue().getFilters();
        assertEquals(1, applied.size());
        assertEquals("GT", applied.get(0).getOp());
        assertEquals("12.50", applied.get(0).getValue());
    }

    @Test
    void shouldRejectInvalidNumberDateAndOperator() {
        when(templateService.getById(10L)).thenReturn(templateWithTwoUserFilters());

        ReportExecutionModels.RunRequest invalidNumberRequest = new ReportExecutionModels.RunRequest();
        invalidNumberRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter number = new ReportExecutionModels.RunFilter();
        number.setKey("meta:loanAmount");
        number.setOp("GT");
        number.setValue("1,200");
        invalidNumberRequest.setFilters(List.of(number));
        assertThrows(ResponseStatusException.class, () -> service.run(invalidNumberRequest, 0, 25));

        ReportExecutionModels.RunRequest invalidDateRequest = new ReportExecutionModels.RunRequest();
        invalidDateRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter date = new ReportExecutionModels.RunFilter();
        date.setKey("meta:applicationDate");
        date.setOp("EQ");
        date.setValue("03/01/2026");
        invalidDateRequest.setFilters(List.of(date));
        assertThrows(ResponseStatusException.class, () -> service.run(invalidDateRequest, 0, 25));

        ReportExecutionModels.RunRequest invalidOpRequest = new ReportExecutionModels.RunRequest();
        invalidOpRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter badOp = new ReportExecutionModels.RunFilter();
        badOp.setKey("meta:loanAmount");
        badOp.setOp("LIKE");
        badOp.setValue("100");
        invalidOpRequest.setFilters(List.of(badOp));
        assertThrows(ResponseStatusException.class, () -> service.run(invalidOpRequest, 0, 25));

        ReportExecutionModels.RunRequest numberLikeRequest = new ReportExecutionModels.RunRequest();
        numberLikeRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter badNumberLike = new ReportExecutionModels.RunFilter();
        badNumberLike.setKey("meta:loanAmount");
        badNumberLike.setOp("LIKE");
        badNumberLike.setValue("12");
        numberLikeRequest.setFilters(List.of(badNumberLike));
        assertThrows(ResponseStatusException.class, () -> service.run(numberLikeRequest, 0, 25));
    }

    @Test
    void shouldAcceptValidNumberRange() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:loanAmount", ReportFilter.FilterLogicalType.NUMBER));

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        request.setTemplateId(10L);
        ReportExecutionModels.RunFilter range = new ReportExecutionModels.RunFilter();
        range.setKey("meta:loanAmount");
        range.setOp("RANGE");
        range.setValueFrom("10");
        range.setValueTo("25");
        request.setFilters(List.of(range));

        service.run(request, 0, 25);

        ArgumentCaptor<DynamicReportRequest> captor = ArgumentCaptor.forClass(DynamicReportRequest.class);
        verify(builder, atLeastOnce()).build(captor.capture(), eq("template:10"));
        ReportFilter applied = captor.getValue().getFilters().get(0);
        assertEquals("RANGE", applied.getOp());
        assertEquals("10", applied.getValueFrom());
        assertEquals("25", applied.getValueTo());
    }

    @Test
    void shouldRejectIncompleteOrDescendingNumberRange() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:loanAmount", ReportFilter.FilterLogicalType.NUMBER));

        ReportExecutionModels.RunRequest missingSide = new ReportExecutionModels.RunRequest();
        missingSide.setTemplateId(10L);
        ReportExecutionModels.RunFilter incomplete = new ReportExecutionModels.RunFilter();
        incomplete.setKey("meta:loanAmount");
        incomplete.setOp("RANGE");
        incomplete.setValueFrom("10");
        missingSide.setFilters(List.of(incomplete));
        assertThrows(ResponseStatusException.class, () -> service.run(missingSide, 0, 25));

        ReportExecutionModels.RunRequest descending = new ReportExecutionModels.RunRequest();
        descending.setTemplateId(10L);
        ReportExecutionModels.RunFilter backwards = new ReportExecutionModels.RunFilter();
        backwards.setKey("meta:loanAmount");
        backwards.setOp("RANGE");
        backwards.setValueFrom("25");
        backwards.setValueTo("10");
        descending.setFilters(List.of(backwards));
        assertThrows(ResponseStatusException.class, () -> service.run(descending, 0, 25));
    }

    @Test
    void shouldAcceptValidDateBetween() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:applicationDate", ReportFilter.FilterLogicalType.DATE));

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        request.setTemplateId(10L);
        ReportExecutionModels.RunFilter between = new ReportExecutionModels.RunFilter();
        between.setKey("meta:applicationDate");
        between.setOp("BETWEEN");
        between.setValueFrom("2026-03-01");
        between.setValueTo("2026-03-31");
        request.setFilters(List.of(between));

        service.run(request, 0, 25);

        ArgumentCaptor<DynamicReportRequest> captor = ArgumentCaptor.forClass(DynamicReportRequest.class);
        verify(builder, atLeastOnce()).build(captor.capture(), eq("template:10"));
        ReportFilter applied = captor.getValue().getFilters().get(0);
        assertEquals("BETWEEN", applied.getOp());
        assertEquals("2026-03-01", applied.getValueFrom());
        assertEquals("2026-03-31", applied.getValueTo());
    }

    @Test
    void shouldRejectIncompleteOrDescendingDateBetween() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:applicationDate", ReportFilter.FilterLogicalType.DATE));

        ReportExecutionModels.RunRequest missingSide = new ReportExecutionModels.RunRequest();
        missingSide.setTemplateId(10L);
        ReportExecutionModels.RunFilter incomplete = new ReportExecutionModels.RunFilter();
        incomplete.setKey("meta:applicationDate");
        incomplete.setOp("BETWEEN");
        incomplete.setValueFrom("2026-03-01");
        missingSide.setFilters(List.of(incomplete));
        assertThrows(ResponseStatusException.class, () -> service.run(missingSide, 0, 25));

        ReportExecutionModels.RunRequest descending = new ReportExecutionModels.RunRequest();
        descending.setTemplateId(10L);
        ReportExecutionModels.RunFilter backwards = new ReportExecutionModels.RunFilter();
        backwards.setKey("meta:applicationDate");
        backwards.setOp("BETWEEN");
        backwards.setValueFrom("2026-03-31");
        backwards.setValueTo("2026-03-01");
        descending.setFilters(List.of(backwards));
        assertThrows(ResponseStatusException.class, () -> service.run(descending, 0, 25));
    }

    @Test
    void shouldAllowStringLikeAndIgnoreBlankLikeValue() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:branch", ReportFilter.FilterLogicalType.STRING));

        ReportExecutionModels.RunRequest likeRequest = new ReportExecutionModels.RunRequest();
        likeRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter like = new ReportExecutionModels.RunFilter();
        like.setKey("meta:branch");
        like.setOp("LIKE");
        like.setValue("avi");
        likeRequest.setFilters(List.of(like));

        service.run(likeRequest, 0, 25);

        ArgumentCaptor<DynamicReportRequest> captor = ArgumentCaptor.forClass(DynamicReportRequest.class);
        verify(builder, atLeastOnce()).build(captor.capture(), eq("template:10"));
        assertEquals("LIKE", captor.getValue().getFilters().get(0).getOp());

        ReportExecutionModels.RunRequest blankLikeRequest = new ReportExecutionModels.RunRequest();
        blankLikeRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter blankLike = new ReportExecutionModels.RunFilter();
        blankLike.setKey("meta:branch");
        blankLike.setOp("LIKE");
        blankLike.setValue("   ");
        blankLikeRequest.setFilters(List.of(blankLike));

        service.run(blankLikeRequest, 0, 25);
    }

    @Test
    void shouldResolvePresetModeDateFilterIntoRangeFilters() {
        when(templateService.getById(10L)).thenReturn(templateWithUserFilter("meta:applicationDate", ReportFilter.FilterLogicalType.DATE));
        when(datePresetService.listPresetsForFilter(eq("r1"), eq("meta:applicationDate")))
                .thenReturn(List.of(new ReportExecutionModels.DatePresetOption("THIS_WEEK", "This Week", 10)));
        when(datePresetService.resolvePreset(eq("r1"), eq("meta:applicationDate"), eq("THIS_WEEK")))
                .thenReturn(new DatePresetService.ResolvedDateRange(java.time.LocalDate.of(2026, 3, 2), java.time.LocalDate.of(2026, 3, 8), "THIS_WEEK"));

        ReportExecutionModels.RunRequest presetRequest = new ReportExecutionModels.RunRequest();
        presetRequest.setTemplateId(10L);
        ReportExecutionModels.RunFilter datePreset = new ReportExecutionModels.RunFilter();
        datePreset.setKey("meta:applicationDate");
        datePreset.setMode(ReportExecutionModels.DateFilterMode.PRESET);
        datePreset.setPresetCode("THIS_WEEK");
        presetRequest.setFilters(List.of(datePreset));

        service.run(presetRequest, 0, 25);

        ArgumentCaptor<DynamicReportRequest> captor = ArgumentCaptor.forClass(DynamicReportRequest.class);
        verify(builder, atLeastOnce()).build(captor.capture(), eq("template:10"));
        List<ReportFilter> applied = captor.getValue().getFilters();
        assertEquals(2, applied.size());
        assertEquals("GE", applied.get(0).getOp());
        assertEquals("LE", applied.get(1).getOp());
    }

    @Test
    void shouldExposeMailConfigWithExecutableTemplate() {
        ReportTemplateResponse template = templateWithUserFilter("meta:branch", ReportFilter.FilterLogicalType.STRING);
        ReportMailConfig mailConfig = new ReportMailConfig();
        mailConfig.setEnabled(true);
        template.getRequest().setMail(mailConfig);
        when(templateService.getById(10L)).thenReturn(template);

        ReportExecutionModels.TemplateDetail detail = service.getExecutableTemplate(10L);

        assertNotNull(detail.mail());
        assertTrue(detail.mail().isEnabled());
    }

    private ReportTemplateResponse templateWithUserFilter(String key, ReportFilter.FilterLogicalType type) {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.DOCUMENT_NUMBER"));

        ReportFilter filter = new ReportFilter();
        filter.setKey(key);
        filter.setMode(ReportFilter.Mode.USER_INPUT);
        filter.setLogicalType(type);
        request.setFilters(List.of(filter));

        return new ReportTemplateResponse(10L, "r1", null, request, "tester", Instant.now());
    }

    private ReportTemplateResponse templateWithTwoUserFilters() {
        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.DOCUMENT_NUMBER"));

        ReportFilter number = new ReportFilter();
        number.setKey("meta:loanAmount");
        number.setMode(ReportFilter.Mode.USER_INPUT);
        number.setLogicalType(ReportFilter.FilterLogicalType.NUMBER);

        ReportFilter date = new ReportFilter();
        date.setKey("meta:applicationDate");
        date.setMode(ReportFilter.Mode.USER_INPUT);
        date.setLogicalType(ReportFilter.FilterLogicalType.DATE);

        request.setFilters(List.of(number, date));
        return new ReportTemplateResponse(10L, "r2", null, request, "tester", Instant.now());
    }
}
