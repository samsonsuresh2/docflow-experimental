package com.docflow.reports.service;

import com.docflow.notification.model.NotificationAttachment;
import com.docflow.reports.dto.ReportExecutionModels;
import com.docflow.reports.dto.ReportMailAttachmentFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class ReportMailComposer {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HEADER_FONT_FAMILY = "Calibri, Segoe UI, Arial, sans-serif";
    private static final String HEADER_TEXT_COLOR = "#ffffff";
    private static final String HEADER_FILL_COLOR = "#1f4e78";
    private static final String CONTENT_FONT_FAMILY = "Calibri, Segoe UI, Arial, sans-serif";
    private static final String CONTENT_TEXT_COLOR = "#1f2937";
    private static final String CONTENT_FILL_COLOR = "#f8fafc";
    private static final String BORDER_COLOR = "#cbd5e1";

    public String buildInlineHtml(String introHtml,
                                  ReportExecutionModels.RunResponse report,
                                  String disclaimerHtml) {
        StringBuilder body = new StringBuilder();
        body.append("<div style=\"font-family:")
                .append(CONTENT_FONT_FAMILY)
                .append(";color:")
                .append(CONTENT_TEXT_COLOR)
                .append(";font-size:14px;line-height:1.5;\">");
        if (StringUtils.hasText(introHtml)) {
            body.append(introHtml.trim());
        }
        body.append("<br/><br/>");
        body.append(renderTable(report));
        if (StringUtils.hasText(disclaimerHtml)) {
            body.append("<br/><br/><div style=\"font-size:12px;color:#64748b;\">")
                    .append(disclaimerHtml.trim())
                    .append("</div>");
        }
        body.append("</div>");
        return body.toString();
    }

    public NotificationAttachment createAttachment(String reportName,
                                                   ReportExecutionModels.RunResponse report,
                                                   ReportMailAttachmentFormat format) throws IOException {
        ReportMailAttachmentFormat safeFormat = format != null ? format : ReportMailAttachmentFormat.CSV;
        return switch (safeFormat) {
            case EXCEL -> createExcelAttachment(reportName, report);
            case CSV -> createCsvAttachment(reportName, report);
        };
    }

    private NotificationAttachment createCsvAttachment(String reportName,
                                                       ReportExecutionModels.RunResponse report) throws IOException {
        String fileName = baseFileName(reportName) + "-" + FILE_STAMP.format(OffsetDateTime.now()) + ".csv";
        Path path = Files.createTempFile("docflow-report-mail-", ".csv");
        Files.writeString(path, renderCsv(report), StandardCharsets.UTF_8);

        NotificationAttachment attachment = new NotificationAttachment();
        attachment.setPath(path.toString());
        attachment.setFileName(fileName);
        attachment.setContentType("text/csv");
        attachment.setDeleteAfterSend(true);
        return attachment;
    }

    private NotificationAttachment createExcelAttachment(String reportName,
                                                         ReportExecutionModels.RunResponse report) throws IOException {
        String fileName = baseFileName(reportName) + "-" + FILE_STAMP.format(OffsetDateTime.now()) + ".xls";
        Path path = Files.createTempFile("docflow-report-mail-", ".xls");
        Files.writeString(path, "\uFEFF" + renderExcelHtml(report), StandardCharsets.UTF_8);

        NotificationAttachment attachment = new NotificationAttachment();
        attachment.setPath(path.toString());
        attachment.setFileName(fileName);
        attachment.setContentType("application/vnd.ms-excel");
        attachment.setDeleteAfterSend(true);
        return attachment;
    }

    private String renderCsv(ReportExecutionModels.RunResponse report) {
        List<String> columns = report.columns();
        String header = String.join(",", columns.stream().map(this::escapeCsv).toList());
        StringBuilder csv = new StringBuilder(header);
        for (Map<String, Object> row : report.rows()) {
            csv.append("\n");
            for (int index = 0; index < columns.size(); index++) {
                if (index > 0) {
                    csv.append(",");
                }
                csv.append(escapeCsv(toDisplayValue(row.get(columns.get(index)))));
            }
        }
        return csv.toString();
    }

    private String renderExcelHtml(ReportExecutionModels.RunResponse report) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\" /><style>"
                + "body{font-family:" + CONTENT_FONT_FAMILY + ";color:" + CONTENT_TEXT_COLOR + ";}"
                + "table{border-collapse:collapse;font-family:" + CONTENT_FONT_FAMILY + ";font-size:12px;}"
                + "th{background:" + HEADER_FILL_COLOR + ";color:" + HEADER_TEXT_COLOR + ";border:1px solid " + BORDER_COLOR + ";padding:6px;font-family:" + HEADER_FONT_FAMILY + ";}"
                + "td{background:" + CONTENT_FILL_COLOR + ";color:" + CONTENT_TEXT_COLOR + ";border:1px solid " + BORDER_COLOR + ";padding:6px;font-family:" + CONTENT_FONT_FAMILY + ";}"
                + "</style></head><body>"
                + renderTable(report)
                + "</body></html>";
    }

    private String renderTable(ReportExecutionModels.RunResponse report) {
        StringBuilder html = new StringBuilder("<table style=\"border-collapse:collapse;border:1px solid ")
                .append(BORDER_COLOR)
                .append(";\">");
        if (!report.columns().isEmpty()) {
            html.append("<thead><tr>");
            for (String column : report.columns()) {
                html.append("<th style=\"background:")
                        .append(HEADER_FILL_COLOR)
                        .append(";color:")
                        .append(HEADER_TEXT_COLOR)
                        .append(";border:1px solid ")
                        .append(BORDER_COLOR)
                        .append(";padding:6px;font-family:")
                        .append(HEADER_FONT_FAMILY)
                        .append(";font-size:12px;font-weight:600;\">")
                        .append(escapeHtml(column))
                        .append("</th>");
            }
            html.append("</tr></thead>");
        }
        html.append("<tbody>");
        for (Map<String, Object> row : report.rows()) {
            html.append("<tr>");
            for (String column : report.columns()) {
                html.append("<td style=\"background:")
                        .append(CONTENT_FILL_COLOR)
                        .append(";color:")
                        .append(CONTENT_TEXT_COLOR)
                        .append(";border:1px solid ")
                        .append(BORDER_COLOR)
                        .append(";padding:6px;font-family:")
                        .append(CONTENT_FONT_FAMILY)
                        .append(";font-size:12px;\">")
                        .append(escapeHtml(toDisplayValue(row.get(column))))
                        .append("</td>");
            }
            html.append("</tr>");
        }
        if (report.rows().isEmpty()) {
            html.append("<tr><td colspan=\"")
                    .append(Math.max(1, report.columns().size()))
                    .append("\" style=\"background:")
                    .append(CONTENT_FILL_COLOR)
                    .append(";color:")
                    .append(CONTENT_TEXT_COLOR)
                    .append(";border:1px solid ")
                    .append(BORDER_COLOR)
                    .append(";padding:6px;font-family:")
                    .append(CONTENT_FONT_FAMILY)
                    .append(";font-size:12px;\">No rows returned.</td></tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String baseFileName(String reportName) {
        String safe = StringUtils.hasText(reportName) ? reportName.trim() : "report";
        safe = safe.replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isBlank() ? "report" : safe;
    }

    private String toDisplayValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String escapeCsv(String value) {
        String safe = value != null ? value : "";
        if (safe.contains("\"") || safe.contains(",") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String escapeHtml(String value) {
        String safe = value != null ? value : "";
        return safe
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
