package com.docflow.reports.service;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReportMultiValueParser {

    private ReportMultiValueParser() {
    }

    public static List<String> parseStringValues(List<String> values, String fallbackCsv) {
        if (values != null && !values.isEmpty()) {
            return normalizeExplicitValues(values);
        }
        return parseEscapedCsv(fallbackCsv);
    }

    public static List<String> parseNumberValues(List<String> values, String fallbackCsv) {
        List<String> normalized = values != null && !values.isEmpty()
                ? normalizeExplicitValues(values)
                : parseSimpleCsv(fallbackCsv);
        for (String value : normalized) {
            try {
                new BigDecimal(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid number format: " + value, ex);
            }
        }
        return normalized;
    }

    public static List<BigDecimal> toBigDecimals(List<String> values) {
        List<BigDecimal> normalized = new ArrayList<>();
        for (String value : values) {
            normalized.add(new BigDecimal(value));
        }
        return normalized;
    }

    private static List<String> normalizeExplicitValues(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Blank values are not allowed");
            }
            String trimmed = value.trim();
            if (!StringUtils.hasText(trimmed)) {
                throw new IllegalArgumentException("Blank values are not allowed");
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one value is required");
        }
        return normalized;
    }

    private static List<String> parseSimpleCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            throw new IllegalArgumentException("At least one value is required");
        }
        String[] parts = csv.split(",", -1);
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            String trimmed = Objects.toString(part, "").trim();
            if (!StringUtils.hasText(trimmed)) {
                throw new IllegalArgumentException("Blank values are not allowed");
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private static List<String> parseEscapedCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            throw new IllegalArgumentException("At least one value is required");
        }
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (escaping) {
                if (ch != ',' && ch != '\\') {
                    throw new IllegalArgumentException("Invalid escape sequence in multi-value input");
                }
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == ',') {
                addParsedToken(values, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (escaping) {
            throw new IllegalArgumentException("Invalid escape sequence in multi-value input");
        }
        addParsedToken(values, current);
        return values;
    }

    private static void addParsedToken(List<String> values, StringBuilder token) {
        String trimmed = token.toString().trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException("Blank values are not allowed");
        }
        values.add(trimmed);
    }
}
