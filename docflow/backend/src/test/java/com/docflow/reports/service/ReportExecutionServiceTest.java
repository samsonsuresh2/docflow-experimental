package com.docflow.reports.service;

import com.docflow.reports.dto.DynamicReportRequest;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportFilter;
import com.docflow.reports.dto.ReportTemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExecutionServiceTest {

    @Mock
    private ReportTemplateService templateService;

    @Mock
    private DynamicReportBuilder builder;

    @Mock
    private DynamicReportExecutor executor;

    @InjectMocks
    private ReportExecutionService service;

    @Captor
    private ArgumentCaptor<DynamicReportRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        when(executor.execute(any(), anyInt(), anyInt())).thenReturn(Map.of(
                "columns", List.of("COL1"),
                "rows", List.of(Map.of("COL1", "v1"))
        ));
        when(builder.build(any(), any())).thenAnswer(invocation -> {
            DynamicReportRequest req = invocation.getArgument(0);
            String context = invocation.getArgument(1);
            return new DynamicReportBuilder.BuiltReport(
                    "SELECT 1",
                    Map.of(),
                    List.of(new DynamicReportBuilder.SelectColumn("c0", "COL1", "COL1")),
                    List.of(),
                    "",
                    "",
                    context
            );
        });
    }

    @Test
    void ignoresBlankFilterValue() {
        ReportTemplateResponse template = templateWithFilter("DOCUMENT_PARENT.ID", "=", "NUMBER", ReportFilter.Mode.USER_INPUT);
        when(templateService.getById(anyLong())).thenReturn(template);

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        ReportExecutionModels.RunFilter filter = new ReportExecutionModels.RunFilter();
        filter.setKey("DOCUMENT_PARENT.ID");
        filter.setOp("=");
        filter.setValue("  "); // blank should be ignored
        request.setTemplateId(template.getId());
        request.setFilters(List.of(filter));

        service.run(request, 0, 25);

        verify(builder).build(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getFilters()).isEmpty();
    }

    @Test
    void rejectsInvalidDateFormat() {
        ReportTemplateResponse template = templateWithFilter("DOCUMENT_PARENT.START_DATE", "=", "DATE", ReportFilter.Mode.USER_INPUT);
        when(templateService.getById(anyLong())).thenReturn(template);

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        ReportExecutionModels.RunFilter filter = new ReportExecutionModels.RunFilter();
        filter.setKey("DOCUMENT_PARENT.START_DATE");
        filter.setOp("=");
        filter.setValue("12/31/2025"); // wrong format
        request.setTemplateId(template.getId());
        request.setFilters(List.of(filter));

        assertThatThrownBy(() -> service.run(request, 0, 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid date format");
        verifyNoInteractions(builder);
    }

    @Test
    void rejectsOperatorNotAllowed() {
        ReportTemplateResponse template = templateWithFilter("DOCUMENT_PARENT.AMOUNT", "=", "NUMBER", ReportFilter.Mode.USER_INPUT);
        when(templateService.getById(anyLong())).thenReturn(template);

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        ReportExecutionModels.RunFilter filter = new ReportExecutionModels.RunFilter();
        filter.setKey("DOCUMENT_PARENT.AMOUNT");
        filter.setOp("<=");
        filter.setValue("100");
        request.setTemplateId(template.getId());
        request.setFilters(List.of(filter));

        assertThatThrownBy(() -> service.run(request, 0, 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Operator not allowed");
        verifyNoInteractions(builder);
    }

    @Test
    void rejectsUnknownFilterKey() {
        ReportTemplateResponse template = templateWithFilter("DOCUMENT_PARENT.STATUS", "=", "TEXT", ReportFilter.Mode.USER_INPUT);
        when(templateService.getById(anyLong())).thenReturn(template);

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        ReportExecutionModels.RunFilter filter = new ReportExecutionModels.RunFilter();
        filter.setKey("DOCUMENT_PARENT.MISSING");
        filter.setOp("=");
        filter.setValue("APPROVED");
        request.setTemplateId(template.getId());
        request.setFilters(List.of(filter));

        assertThatThrownBy(() -> service.run(request, 0, 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown filter key");
        verifyNoInteractions(builder);
    }

    @Test
    void rejectsTextComparisonWithLessThan() {
        ReportTemplateResponse template = templateWithFilter("DOCUMENT_PARENT.STATUS", "=", "TEXT", ReportFilter.Mode.USER_INPUT);
        when(templateService.getById(anyLong())).thenReturn(template);

        ReportExecutionModels.RunRequest request = new ReportExecutionModels.RunRequest();
        ReportExecutionModels.RunFilter filter = new ReportExecutionModels.RunFilter();
        filter.setKey("DOCUMENT_PARENT.STATUS");
        filter.setOp("<");
        filter.setValue("APPROVED");
        request.setTemplateId(template.getId());
        request.setFilters(List.of(filter));

        assertThatThrownBy(() -> service.run(request, 0, 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Operator not allowed");
        verifyNoInteractions(builder);
    }

    private ReportTemplateResponse templateWithFilter(String key, String op, String dataType, ReportFilter.Mode mode) {
        ReportFilter filter = new ReportFilter();
        filter.setKey(key);
        filter.setOp(op);
        filter.setMode(mode);
        filter.setDataType(dataType);

        DynamicReportRequest request = new DynamicReportRequest();
        request.setBaseEntity("DOCUMENT_PARENT");
        request.setColumns(List.of("DOCUMENT_PARENT.ID"));
        request.setFilters(List.of(filter));

        return new ReportTemplateResponse(1L, "Test Template", null, request, "admin1", Instant.now());
    }
}
