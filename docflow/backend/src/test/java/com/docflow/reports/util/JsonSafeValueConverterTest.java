package com.docflow.reports.util;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialClob;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSafeValueConverterTest {

    @Test
    void convertsCommonJsonSafeValuesAsExpected() throws Exception {
        assertThat(JsonSafeValueConverter.convert(null)).isNull();
        assertThat(JsonSafeValueConverter.convert("text")).isEqualTo("text");
        assertThat(JsonSafeValueConverter.convert(12)).isEqualTo(12);
        assertThat(JsonSafeValueConverter.convert(true)).isEqualTo(true);
        assertThat(JsonSafeValueConverter.convert(Timestamp.from(Instant.parse("2026-04-27T00:00:00Z"))))
            .isEqualTo("2026-04-27T00:00:00Z");
        assertThat(JsonSafeValueConverter.convert(Date.valueOf("2026-04-27"))).isEqualTo("2026-04-27");
        assertThat(JsonSafeValueConverter.convert(java.util.Date.from(Instant.parse("2026-04-27T01:02:03Z"))))
            .isEqualTo("2026-04-27T01:02:03Z");
        assertThat(JsonSafeValueConverter.convert(LocalDate.parse("2026-04-27"))).isEqualTo("2026-04-27");
        assertThat(JsonSafeValueConverter.convert(LocalDateTime.parse("2026-04-27T10:15:30"))).isEqualTo("2026-04-27T10:15:30");
        assertThat(JsonSafeValueConverter.convert(OffsetDateTime.parse("2026-04-27T10:15:30+05:30"))).isEqualTo("2026-04-27T04:45:30Z");
        assertThat(JsonSafeValueConverter.convert(Instant.parse("2026-04-27T01:02:03Z"))).isEqualTo("2026-04-27T01:02:03Z");
        assertThat(JsonSafeValueConverter.convert(new SerialClob("clob text".toCharArray()))).isEqualTo("clob text");
    }

    @Test
    void returnsOriginalValueWhenNoConversionApplies() {
        Object value = new Object();

        assertThat(JsonSafeValueConverter.convert(value)).isSameAs(value);
    }

    @Test
    void wrapsClobReadFailures() {
        var clob = new BrokenClob();

        assertThatThrownBy(() -> JsonSafeValueConverter.convert(clob))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to read CLOB");
    }

    private static final class BrokenClob implements Clob {

        @Override
        public Reader getCharacterStream() throws SQLException {
            throw new SQLException("boom");
        }

        @Override public long length() { return 0; }
        @Override public String getSubString(long pos, int length) { return null; }
        @Override public InputStream getAsciiStream() { return null; }
        @Override public long position(String searchstr, long start) { return -1; }
        @Override public long position(Clob searchstr, long start) { return -1; }
        @Override public int setString(long pos, String str) { return 0; }
        @Override public int setString(long pos, String str, int offset, int len) { return 0; }
        @Override public OutputStream setAsciiStream(long pos) { return null; }
        @Override public Writer setCharacterStream(long pos) { return null; }
        @Override public void truncate(long len) { }
        @Override public void free() { }
        @Override public Reader getCharacterStream(long pos, long length) { return null; }
    }
}
