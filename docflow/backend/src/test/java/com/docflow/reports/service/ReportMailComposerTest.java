package com.docflow.reports.service;

import com.docflow.notification.model.NotificationAttachment;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportMailAttachmentFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMailComposerTest {

    private final ReportMailComposer composer = new ReportMailComposer();

    @Test
    void buildInlineHtmlEscapesTableDataAndRendersDisclaimer() {
        var report = report(
            List.of("Name", "Amount"),
            List.of(Map.of("Name", "<Alice & Bob>", "Amount", "10"))
        );

        String html = composer.buildInlineHtml("<p>Hello</p>", report, "<em>Internal</em>");

        assertThat(html).contains("<p>Hello</p>");
        assertThat(html).contains("&lt;Alice &amp; Bob&gt;");
        assertThat(html).contains("<em>Internal</em>");
    }

    @Test
    void buildInlineHtmlShowsEmptyStateWhenReportHasNoRows() {
        String html = composer.buildInlineHtml(null, report(List.of("Name"), List.of()), null);

        assertThat(html).contains("No rows returned.");
        assertThat(html).contains("colspan=\"1\"");
    }

    @Test
    void createCsvAttachmentWritesEscapedCsvAndSafeFilename() throws Exception {
        var report = report(
            List.of("Name", "Notes"),
            List.of(Map.of("Name", "Alice", "Notes", "Line 1\n\"Quoted\", value"))
        );

        NotificationAttachment attachment = composer.createAttachment("Loan Report / Q1", report, ReportMailAttachmentFormat.CSV);

        assertThat(attachment.getFileName()).startsWith("Loan_Report_Q1-").endsWith(".csv");
        assertThat(attachment.getContentType()).isEqualTo("text/csv");
        assertThat(attachment.isDeleteAfterSend()).isTrue();
        assertThat(Files.readString(Path.of(attachment.getPath())))
            .contains("Name,Notes")
            .contains("\"Line 1\n\"\"Quoted\"\", value\"");
    }

    @Test
    void createExcelAttachmentWritesHtmlWorkbookWhenRequested() throws Exception {
        var report = report(List.of("Name"), List.of(Map.of("Name", "Alice")));

        NotificationAttachment attachment = composer.createAttachment(" ", report, ReportMailAttachmentFormat.EXCEL);

        assertThat(attachment.getFileName()).startsWith("report-").endsWith(".xls");
        assertThat(attachment.getContentType()).isEqualTo("application/vnd.ms-excel");
        assertThat(Files.readString(Path.of(attachment.getPath()))).contains("<!DOCTYPE html>").contains("Alice");
    }

    @Test
    void createAttachmentDefaultsToCsvWhenFormatIsNull() throws Exception {
        NotificationAttachment attachment = composer.createAttachment(null, report(List.of("Name"), List.of()), null);

        assertThat(attachment.getFileName()).startsWith("report-").endsWith(".csv");
        assertThat(attachment.getContentType()).isEqualTo("text/csv");
    }

    private ReportExecutionModels.RunResponse report(List<String> columns, List<Map<String, Object>> rows) {
        return new ReportExecutionModels.RunResponse(columns, rows, rows.size());
    }
}
