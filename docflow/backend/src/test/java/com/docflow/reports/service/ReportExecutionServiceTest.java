package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportFilter;
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
    private ReportExecutionService service;

    @BeforeEach
    void setUp() {
        templateService = mock(ReportTemplateService.class);
        builder = mock(DynamicReportBuilder.class);
        executor = mock(DynamicReportExecutor.class);
        service = new ReportExecutionService(templateService, builder, executor);

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
        assertEquals(List.of("EQ", "LT", "GT"), detail.filters().get(0).allowedOps());
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
