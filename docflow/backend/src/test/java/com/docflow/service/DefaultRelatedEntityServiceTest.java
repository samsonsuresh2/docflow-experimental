package com.docflow.service;

import com.docflow.api.dto.RelatedEntityResponse;
import com.docflow.reports.config.ReportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRelatedEntityServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ReportProperties properties;
    private DefaultRelatedEntityService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:related-" + System.nanoTime() + ";MODE=Oracle;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        ));
        jdbcTemplate.execute("CREATE TABLE DOCUMENT_PARENT (ID BIGINT PRIMARY KEY, LOAN_ID VARCHAR(20))");
        jdbcTemplate.execute("CREATE TABLE LOAN_DATA (LOAN_ID VARCHAR(20), CUSTOMER_NAME VARCHAR(100), AMOUNT NUMERIC)");
        jdbcTemplate.update("INSERT INTO DOCUMENT_PARENT (ID, LOAN_ID) VALUES (?, ?)", 10L, "LN-1");
        jdbcTemplate.update("INSERT INTO DOCUMENT_PARENT (ID, LOAN_ID) VALUES (?, ?)", 11L, null);
        jdbcTemplate.update("INSERT INTO LOAN_DATA (LOAN_ID, CUSTOMER_NAME, AMOUNT) VALUES (?, ?, ?)", "LN-1", "Alice", 125);

        properties = new ReportProperties();
        properties.getDocumentTable().setName("DOCUMENT_PARENT");
        properties.getDocumentTable().setInternalPk("ID");

        ReportProperties.EntityProperties entity = new ReportProperties.EntityProperties();
        entity.setName("LOAN_DATA");
        entity.setLabel("Loan Data");
        entity.getJoinToDocument().setEnabled(true);
        entity.getJoinToDocument().setDocumentFkColumn("LOAN_ID");
        entity.getJoinToDocument().setBusinessFkColumn("LOAN_ID");
        properties.setEntities(List.of(entity));

        service = new DefaultRelatedEntityService(jdbcTemplate, properties);
    }

    @Test
    void getRelatedEntityMapsColumnsAndRows() {
        RelatedEntityResponse response = service.getRelatedEntity(10L, "loan_data");

        assertThat(response.getEntityName()).isEqualTo("LOAN_DATA");
        assertThat(response.getLabel()).isEqualTo("Loan Data");
        assertThat(response.getColumns()).extracting("key").containsExactly("LOAN_ID", "CUSTOMER_NAME", "AMOUNT");
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0))
            .containsEntry("LOAN_ID", "LN-1")
            .containsEntry("CUSTOMER_NAME", "Alice");
        assertThat(response.getRows().get(0).get("AMOUNT").toString()).isEqualTo("125");
    }

    @Test
    void getRelatedEntityReturnsEmptyResponseWhenJoinValueBlank() {
        RelatedEntityResponse response = service.getRelatedEntity(11L, "LOAN_DATA");

        assertThat(response.getColumns()).isEmpty();
        assertThat(response.getRows()).isEmpty();
    }

    @Test
    void getRelatedEntityRejectsUnknownOrMisconfiguredEntities() {
        assertThatThrownBy(() -> service.getRelatedEntity(10L, ""))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> service.getRelatedEntity(10L, "MISSING"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);

        properties.getEnabledEntities().get(0).getJoinToDocument().setEnabled(false);
        assertThatThrownBy(() -> service.getRelatedEntity(10L, "LOAN_DATA"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
