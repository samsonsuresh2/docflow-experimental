package com.docflow.reports.service;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReportFilterOperators {

    public static final List<String> STRING_OPS = List.of("EQ", "LIKE", "IN", "NOT_IN");
    public static final List<String> NUMBER_OPS = List.of("EQ", "LT", "GT", "RANGE", "IN", "NOT_IN");
    public static final List<String> DATE_OPS = List.of("EQ", "LT", "GT", "BETWEEN");
    public static final Set<String> ALL_SUPPORTED = Set.of("EQ", "LIKE", "IN", "NOT_IN", "LT", "GT", "GE", "LE", "RANGE", "BETWEEN");

    private ReportFilterOperators() {
    }

    public static String normalize(String op) {
        if (!StringUtils.hasText(op)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter operator required");
        }
        return switch (op.trim().toUpperCase(Locale.ROOT)) {
            case "=" -> "EQ";
            case "<" -> "LT";
            case ">" -> "GT";
            default -> op.trim().toUpperCase(Locale.ROOT);
        };
    }

    public static boolean requiresRangeValues(String op) {
        return "RANGE".equals(op) || "BETWEEN".equals(op);
    }

    public static boolean requiresMultiValues(String op) {
        return "IN".equals(op) || "NOT_IN".equals(op);
    }
}
