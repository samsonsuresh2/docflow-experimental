export const REQUIRE_AT_LEAST_ONE_REPORT_FILTER = true;
export const AT_LEAST_ONE_REPORT_FILTER_MESSAGE = 'At least one filter value is required to run the report.';

export function validateAtLeastOneReportFilter(filters: unknown[] | null | undefined): string | null {
  if (!REQUIRE_AT_LEAST_ONE_REPORT_FILTER) {
    return null;
  }
  return filters && filters.length > 0 ? null : AT_LEAST_ONE_REPORT_FILTER_MESSAGE;
}
