package com.docflow.web;

import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.OracleContainer;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "docflow.security.auth-mode=HEADER_AUTH")
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfSystemProperty(named = "docflow.docker.tests", matches = "true")
class RelatedEntityControllerTest {

    @Container
    @SuppressWarnings("resource")
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-xe:21-slim");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
        registry.add("spring.datasource.driver-class-name", ORACLE::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.OracleDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE DOCUMENT_PARENT ADD CLIENT_ID VARCHAR2(100)");
        } catch (DataAccessException ignored) {
            // column exists
        }
        try {
            jdbcTemplate.execute("CREATE TABLE LOAN_DATA (LOAN_NO VARCHAR2(50), USER_ID VARCHAR2(100))");
        } catch (DataAccessException ignored) {
            // table exists
        }
        jdbcTemplate.update("DELETE FROM LOAN_DATA");
    }

    @Test
    void relatedEntityUsesDocumentAndBusinessJoinColumns() throws Exception {
        DocumentParent document = new DocumentParent();
        document.setDocumentNumber("DOC-9001");
        document.setTitle("Loan Test");
        document.setStatus(DocumentStatus.DRAFT);
        document.setCreatedBy("maker1");
        document.setCreatedAt(OffsetDateTime.now());
        DocumentParent saved = documentRepository.save(document);

        jdbcTemplate.update("UPDATE DOCUMENT_PARENT SET CLIENT_ID = ? WHERE ID = ?", "client-123", saved.getId());
        jdbcTemplate.update("INSERT INTO LOAN_DATA (LOAN_NO, USER_ID) VALUES (?, ?)", "LN-1", "client-123");

        mockMvc.perform(get("/api/documents/{id}/related-entities/LOAN_DATA", saved.getId())
                .header("X-USER-ID", "tester"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityName").value("LOAN_DATA"))
            .andExpect(jsonPath("$.label").value("Loans"))
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].USER_ID").value("client-123"));
    }
}
