package com.docflow.reports.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public final class ReportRunPolicy {

    public static final boolean REQUIRE_AT_LEAST_ONE_RUNTIME_FILTER = true;
    public static final String AT_LEAST_ONE_FILTER_MESSAGE = "At least one filter value is required to run the report.";

    private ReportRunPolicy() {
    }

    public static void validateAtLeastOneRuntimeFilter(List<?> filters) {
        if (!REQUIRE_AT_LEAST_ONE_RUNTIME_FILTER) {
            return;
        }
        if (filters == null || filters.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AT_LEAST_ONE_FILTER_MESSAGE);
        }
    }
}
