package com.docflow.reports.service;

import com.docflow.reports.config.ReportProperties;
import com.docflow.reports.dto.ReportExecutionModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DatePresetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatePresetService.class);
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReportProperties properties;

    public DatePresetService(NamedParameterJdbcTemplate jdbcTemplate, ReportProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<ReportExecutionModels.DatePresetOption> listPresetsForFilter(String reportCode, String filterKey) {
        String sql = """
                SELECT m.PRESET_CODE, m.PRESET_NAME, NVL(mp.DISPLAY_ORDER, m.DISPLAY_ORDER) AS DISPLAY_ORDER
                FROM REPORT_FILTER_PRESET_MAP mp
                JOIN DATE_PRESET_MASTER m ON m.PRESET_CODE = mp.PRESET_CODE
                WHERE mp.ENABLED = 'Y'
                  AND m.ENABLED = 'Y'
                  AND mp.REPORT_CODE = :reportCode
                  AND mp.FILTER_KEY = :filterKey
                ORDER BY NVL(mp.DISPLAY_ORDER, m.DISPLAY_ORDER), m.PRESET_NAME
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("reportCode", reportCode)
                .addValue("filterKey", filterKey);
        try {
            return jdbcTemplate.query(sql, params, (rs, i) ->
                    new ReportExecutionModels.DatePresetOption(rs.getString("PRESET_CODE"), rs.getString("PRESET_NAME"), rs.getInt("DISPLAY_ORDER"))
            );
        } catch (DataAccessException ex) {
            LOGGER.warn("Falling back to no preset options for reportCode={} filterKey={} because preset lookup failed",
                    reportCode, filterKey, ex);
            return List.of();
        }
    }

    public ResolvedDateRange resolvePreset(String reportCode, String filterKey, String presetCode) {
        if (!StringUtils.hasText(presetCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presetCode is required in PRESET mode");
        }

        String normalizedCode = presetCode.trim().toUpperCase(Locale.ROOT);
        DatePresetDefinition preset = loadPreset(reportCode, filterKey, normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Preset not found / disabled / unmapped for filter: " + normalizedCode));

        LocalDate today = LocalDate.now();
        LocalDate from = resolveRule(preset.startRule(), today);
        LocalDate to = resolveRule(preset.endRule(), today);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved preset range is invalid for " + normalizedCode);
        }
        return new ResolvedDateRange(from, to, normalizedCode);
    }

    private Optional<DatePresetDefinition> loadPreset(String reportCode, String filterKey, String presetCode) {
        String sql = """
                SELECT m.PRESET_CODE, m.START_RULE, m.END_RULE
                FROM DATE_PRESET_MASTER m
                JOIN REPORT_FILTER_PRESET_MAP mp ON mp.PRESET_CODE = m.PRESET_CODE
                WHERE m.PRESET_CODE = :presetCode
                  AND m.ENABLED = 'Y'
                  AND mp.ENABLED = 'Y'
                  AND mp.REPORT_CODE = :reportCode
                  AND mp.FILTER_KEY = :filterKey
                """;
        try {
            List<DatePresetDefinition> rows = jdbcTemplate.query(sql,
                    new MapSqlParameterSource()
                            .addValue("presetCode", presetCode)
                            .addValue("reportCode", reportCode)
                            .addValue("filterKey", filterKey),
                    (rs, i) -> new DatePresetDefinition(rs.getString("PRESET_CODE"), rs.getString("START_RULE"), rs.getString("END_RULE")));
            return rows.stream().findFirst().or(() -> builtInPreset(presetCode));
        } catch (DataAccessException ex) {
            LOGGER.warn("Falling back to built-in preset resolution for reportCode={} filterKey={} presetCode={} because preset lookup failed",
                    reportCode, filterKey, presetCode, ex);
            return builtInPreset(presetCode);
        }
    }

    private Optional<DatePresetDefinition> builtInPreset(String presetCode) {
        return switch (presetCode) {
            case "THIS_WEEK" -> Optional.of(new DatePresetDefinition("THIS_WEEK", "CURRENT_WEEK_START", "TODAY"));
            case "PREVIOUS_WEEK" -> Optional.of(new DatePresetDefinition("PREVIOUS_WEEK", "PREVIOUS_WEEK_START", "PREVIOUS_WEEK_END"));
            case "THIS_MONTH" -> Optional.of(new DatePresetDefinition("THIS_MONTH", "CURRENT_MONTH_START", "TODAY"));
            case "PREVIOUS_MONTH" -> Optional.of(new DatePresetDefinition("PREVIOUS_MONTH", "PREVIOUS_MONTH_START", "PREVIOUS_MONTH_END"));
            default -> Optional.empty();
        };
    }

    LocalDate resolveRule(String rule, LocalDate today) {
        if (!StringUtils.hasText(rule)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preset rule is empty");
        }

        String trimmed = rule.trim().toUpperCase(Locale.ROOT);
        String anchor = trimmed;
        int offset = 0;
        int plus = trimmed.lastIndexOf('+');
        int minus = trimmed.lastIndexOf('-');
        int split = Math.max(plus, minus);
        if (split > 0) {
            anchor = trimmed.substring(0, split);
            offset = Integer.parseInt(trimmed.substring(split));
        }

        LocalDate base = switch (anchor) {
            case "TODAY" -> today;
            case "CURRENT_WEEK_START" -> startOfCurrentWeek(today);
            case "CURRENT_WEEK_END" -> startOfCurrentWeek(today).plusDays(6);
            case "PREVIOUS_WEEK_START" -> startOfCurrentWeek(today).minusWeeks(1);
            case "PREVIOUS_WEEK_END" -> startOfCurrentWeek(today).minusDays(1);
            case "CURRENT_MONTH_START" -> today.withDayOfMonth(1);
            case "CURRENT_MONTH_END" -> today.with(TemporalAdjusters.lastDayOfMonth());
            case "PREVIOUS_MONTH_START" -> today.minusMonths(1).withDayOfMonth(1);
            case "PREVIOUS_MONTH_END" -> today.withDayOfMonth(1).minusDays(1);
            default -> resolveDayAnchor(anchor, today);
        };
        return base.plusDays(offset);
    }

    private LocalDate resolveDayAnchor(String anchor, LocalDate today) {
        if (anchor.startsWith("CURRENT_")) {
            DayOfWeek day = DayOfWeek.valueOf(anchor.substring("CURRENT_".length()));
            LocalDate start = startOfCurrentWeek(today);
            return start.with(TemporalAdjusters.nextOrSame(day));
        }
        if (anchor.startsWith("PREVIOUS_")) {
            DayOfWeek day = DayOfWeek.valueOf(anchor.substring("PREVIOUS_".length()));
            LocalDate start = startOfCurrentWeek(today).minusWeeks(1);
            return start.with(TemporalAdjusters.nextOrSame(day));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown preset rule anchor: " + anchor);
    }

    private LocalDate startOfCurrentWeek(LocalDate today) {
        DayOfWeek startDay = parseWeekStartDay(properties.getWeekStartDay());
        int diff = (7 + (today.getDayOfWeek().getValue() - startDay.getValue())) % 7;
        return today.minusDays(diff);
    }

    private DayOfWeek parseWeekStartDay(String value) {
        if (!StringUtils.hasText(value)) {
            return DayOfWeek.MONDAY;
        }
        try {
            return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DayOfWeek.MONDAY;
        }
    }

    private record DatePresetDefinition(String code, String startRule, String endRule) {
    }

    public record ResolvedDateRange(LocalDate fromDate, LocalDate toDate, String presetCode) {
    }
}
