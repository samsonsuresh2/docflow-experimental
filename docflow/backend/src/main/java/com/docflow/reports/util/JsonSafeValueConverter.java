package com.docflow.reports.util;

import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class JsonSafeValueConverter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private JsonSafeValueConverter() {
    }

    public static Object convert(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Timestamp timestamp) {
            return INSTANT_FORMATTER.format(timestamp.toInstant());
        }
        if (value instanceof Date date) {
            return DATE_FORMATTER.format(date.toLocalDate());
        }
        if (value instanceof java.util.Date utilDate) {
            Instant instant = utilDate.toInstant();
            return INSTANT_FORMATTER.format(instant);
        }
        if (value instanceof LocalDate localDate) {
            return DATE_FORMATTER.format(localDate);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return LOCAL_DATE_TIME_FORMATTER.format(localDateTime);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return INSTANT_FORMATTER.format(instant);
        }
        if (value instanceof Clob clob) {
            return readClob(clob);
        }
        Object oracleConverted = tryConvertOracleValue(value);
        return oracleConverted != null ? oracleConverted : value;
    }

    private static Object tryConvertOracleValue(Object value) {
        String className = value.getClass().getName();
        if (className.equals("oracle.sql.TIMESTAMP")
                || className.equals("oracle.sql.TIMESTAMPTZ")
                || className.equals("oracle.sql.TIMESTAMPLTZ")) {
            Timestamp timestamp = invokeOracleTimestamp(value);
            if (timestamp != null) {
                return INSTANT_FORMATTER.format(timestamp.toInstant());
            }
        }
        if (className.equals("oracle.sql.DATE")) {
            Date date = invokeOracleDate(value);
            if (date != null) {
                return DATE_FORMATTER.format(date.toLocalDate());
            }
        }
        return null;
    }

    private static Timestamp invokeOracleTimestamp(Object value) {
        try {
            return (Timestamp) value.getClass().getMethod("timestampValue").invoke(value);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            return null;
        }
    }

    private static Date invokeOracleDate(Object value) {
        try {
            return (Date) value.getClass().getMethod("dateValue").invoke(value);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            Timestamp timestamp = invokeOracleTimestamp(value);
            if (timestamp != null) {
                return Date.valueOf(timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
            }
            return null;
        }
    }

    private static String readClob(Clob clob) {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (SQLException | IOException ex) {
            throw new IllegalStateException("Failed to read CLOB", ex);
        }
    }
}
