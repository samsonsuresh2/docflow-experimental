package com.docflow.service;

import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.context.RequestUser;
import com.docflow.service.config.DataInjectorProperties;
import com.docflow.service.config.ExcelHeaderMappingResolver;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDataInjectorServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DataInjectorProperties properties;
    private DefaultDataInjectorService service;

    @BeforeEach
    void setUp() {
        properties = new DataInjectorProperties();
        properties.setTargetTable("LOAN_DATA");
        properties.setPrimaryKey("LOAN_ID");
        properties.setMappings(Map.of(
            "Loan Id", "LOAN_ID",
            "Customer Name", "CUSTOMER_NAME",
            "Product Code", "PRODUCT_CODE"
        ));
        properties.setExtractors(Map.of("PRODUCT_CODE", "([A-Z]{2}-\\d+)"));
        service = new DefaultDataInjectorService(
            jdbcTemplate,
            properties,
            new ExcelHeaderMappingResolver(properties)
        );
    }

    @Test
    void uploadExcelInsertsUpdatesSkipsAndReportsIgnoredColumns() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "loans.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes()
        );

        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM LOAN_DATA WHERE LOAN_ID = ?"), eq(Integer.class), any()))
            .thenReturn(0, 1, 1);
        when(jdbcTemplate.update(eq("INSERT INTO LOAN_DATA (LOAN_ID, CUSTOMER_NAME, PRODUCT_CODE) VALUES (?, ?, ?)"), any(Object[].class)))
            .thenReturn(1);
        when(jdbcTemplate.update(eq("UPDATE LOAN_DATA SET CUSTOMER_NAME = ?, PRODUCT_CODE = ? WHERE LOAN_ID = ?"), any(Object[].class)))
            .thenReturn(1);

        DataInjectorResponse response = service.uploadExcel(file, new RequestUser("maker", Set.of("MAKER"), "MAKER"));

        assertThat(response.getTotalRows()).isEqualTo(4);
        assertThat(response.getInserted()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(1);
        assertThat(response.getSkipped()).isEqualTo(2);
        assertThat(response.getIgnoredColumns()).containsExactly("Unmapped");
    }

    @Test
    void uploadExcelRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> service.uploadExcel(file, new RequestUser("maker", Set.of("MAKER"), "MAKER")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must contain data");
    }

    @Test
    void uploadExcelRejectsMissingPrimaryKeyHeader() throws Exception {
        properties.setMappings(Map.of("Customer Name", "CUSTOMER_NAME"));
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "loans.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookWithoutPrimaryKeyBytes()
        );

        assertThatThrownBy(() -> service.uploadExcel(file, new RequestUser("maker", Set.of("MAKER"), "MAKER")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("primary key");
    }

    @Test
    void uploadExcelRejectsUnsafeTableNameBeforeReadingFile() {
        properties.setTargetTable("LOAN_DATA;DROP");
        MockMultipartFile file = new MockMultipartFile("file", "loans.xlsx", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> service.uploadExcel(file, new RequestUser("maker", Set.of("MAKER"), "MAKER")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid table name");
    }

    @Test
    void uploadExcelRejectsMissingPrimaryKeyConfiguration() {
        properties.setPrimaryKey(" ");
        MockMultipartFile file = new MockMultipartFile("file", "loans.xlsx", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> service.uploadExcel(file, new RequestUser("maker", Set.of("MAKER"), "MAKER")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("primary-key");
    }

    @Test
    void uploadExcelUsesSqlConvertedValuesForInsert() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "loans.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbookBytes()
        );
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(1) FROM LOAN_DATA WHERE LOAN_ID = ?"), eq(Integer.class), any()))
            .thenReturn(0, 1, 1);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);

        service.uploadExcel(file, new RequestUser("maker", Set.of("MAKER"), "MAKER"));

        verify(jdbcTemplate).update(eq("INSERT INTO LOAN_DATA (LOAN_ID, CUSTOMER_NAME, PRODUCT_CODE) VALUES (?, ?, ?)"), params.capture());
        assertThat(params.getValue()).containsExactly(1001L, "Alice", "PL-1");
    }

    private byte[] workbookBytes() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("loans");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Loan Id");
            header.createCell(1).setCellValue("Customer Name");
            header.createCell(2).setCellValue("Product Code");
            header.createCell(3).setCellValue("Unmapped");

            Row insert = sheet.createRow(1);
            insert.createCell(0).setCellValue(1001);
            insert.createCell(1).setCellValue(" Alice ");
            insert.createCell(2).setCellValue("Product PL-1");
            insert.createCell(3).setCellValue("ignored");

            Row update = sheet.createRow(2);
            update.createCell(0).setCellValue("1002");
            update.createCell(1).setCellValue("Bob");
            update.createCell(2).setCellValue("Product PL-2");

            Row missingPrimaryKey = sheet.createRow(3);
            missingPrimaryKey.createCell(1).setCellValue("No Id");

            Row noUpdateColumns = sheet.createRow(4);
            noUpdateColumns.createCell(0).setCellValue("1003");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithoutPrimaryKeyBytes() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("loans");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Customer Name");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("Alice");
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
