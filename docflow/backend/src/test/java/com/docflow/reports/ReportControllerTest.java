package com.docflow.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reports;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_data (entity_id BIGINT, column_key VARCHAR(100), column_value VARCHAR(255))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS loan_data (entity_id BIGINT, column_key VARCHAR(100), column_value VARCHAR(255))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS report_templates (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, request_json CLOB NOT NULL, created_by VARCHAR(255) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("DELETE FROM user_data");
        jdbcTemplate.execute("DELETE FROM loan_data");
        jdbcTemplate.execute("DELETE FROM report_templates");

        jdbcTemplate.update("INSERT INTO user_data (entity_id, column_key, column_value) VALUES (?,?,?)", 1, "user_id", "U-1");
        jdbcTemplate.update("INSERT INTO user_data (entity_id, column_key, column_value) VALUES (?,?,?)", 1, "first_name", "Alice");
        jdbcTemplate.update("INSERT INTO user_data (entity_id, column_key, column_value) VALUES (?,?,?)", 1, "status", "active");

        jdbcTemplate.update("INSERT INTO loan_data (entity_id, column_key, column_value) VALUES (?,?,?)", 100, "user_id", "U-1");
        jdbcTemplate.update("INSERT INTO loan_data (entity_id, column_key, column_value) VALUES (?,?,?)", 100, "loan_amount", "5000");
        jdbcTemplate.update("INSERT INTO loan_data (entity_id, column_key, column_value) VALUES (?,?,?)", 100, "status", "approved");
    }

    @Test
    void listsAvailableEntities() throws Exception {
        mockMvc.perform(get("/api/reports/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities[0]").value("loan_data"))
                .andExpect(jsonPath("$.entities[1]").value("user_data"));
    }

    @Test
    void exposesMetadataForEntity() throws Exception {
        mockMvc.perform(get("/api/reports/meta").param("entity", "loan_data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("loan_data"))
                .andExpect(jsonPath("$.availableKeys[0]").value("loan_amount"))
                .andExpect(jsonPath("$.availableKeys[1]").value("status"))
                .andExpect(jsonPath("$.availableKeys[2]").value("user_id"))
                .andExpect(jsonPath("$.relationships[0].to").value("user_data"))
                .andExpect(jsonPath("$.relationships[0].via").value("user_id"));
    }

    @Test
    void runsDynamicReport() throws Exception {
        String payload = """
                {
                  \"baseEntity\": \"user_data\",
                  \"columns\": [\"first_name\", \"loan_data.loan_amount\"],
                  \"filters\": [
                    {"key": "loan_data.status", "op": "=", "value": "approved"}
                  ],
                  \"joins\": [
                    {"rightEntity": "loan_data", "on": "user_id=user_id"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/reports/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0]").value("entity_id"))
                .andExpect(jsonPath("$.columns[1]").value("first_name"))
                .andExpect(jsonPath("$.columns[2]").value("loan_data.loan_amount"))
                .andExpect(jsonPath("$.rows[0].first_name").value("Alice"))
                .andExpect(jsonPath("$.rows[0]['loan_data.loan_amount']").value("5000"));
    }

    @Test
    void savesAndListsTemplates() throws Exception {
        String request = """
                {
                  \"name\": \"Loan approvals\",
                  \"request\": {
                    \"baseEntity\": \"loan_data\",
                    \"columns\": [\"entity_id\", \"loan_amount\"],
                    \"filters\": [
                      {"key": "status", "op": "=", "value": "approved"}
                    ],
                    \"joins\": []
                  }
                }
                """;

        mockMvc.perform(post("/api/reports/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-USER-ID", "tester")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Loan approvals"))
                .andExpect(jsonPath("$.createdBy").value("tester"));

        mockMvc.perform(get("/api/reports/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates[0].name").value("Loan approvals"))
                .andExpect(jsonPath("$.templates[0].request.baseEntity").value("loan_data"));
    }
}
