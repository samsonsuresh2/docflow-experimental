package com.docflow.web;

import com.docflow.context.RequestUser;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.DocumentRepository;
import com.docflow.service.AuditService;
import com.docflow.service.DocumentLifecycleEventCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DocumentTimelineControllerTest {

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
    private AuditService auditService;

    @Test
    void timelineReturnsLifecycleEntriesInAscendingOrder() throws Exception {
        RequestUser user = new RequestUser("reviewer1", Set.of("REVIEWER"));

        DocumentParent document = new DocumentParent();
        document.setDocumentNumber("DOC-2001");
        document.setTitle("KYC Update");
        document.setStatus(DocumentStatus.DRAFT);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(OffsetDateTime.now().minusDays(2));
        DocumentParent saved = documentRepository.save(document);

        OffsetDateTime first = OffsetDateTime.now().minusHours(2);
        OffsetDateTime second = OffsetDateTime.now().minusHours(1);

        auditService.logLifecycleEvent(saved, DocumentStatus.DRAFT, DocumentStatus.OPEN,
            DocumentLifecycleEventCatalog.SUBMITTED_FOR_REVIEW, "Submitted", user, first);
        auditService.logLifecycleEvent(saved, DocumentStatus.OPEN, DocumentStatus.UNDER_REVIEW,
            DocumentLifecycleEventCatalog.REVIEW_STARTED, null, user, second);

        mockMvc.perform(get("/api/documents/{id}/timeline", saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventCode").value(DocumentLifecycleEventCatalog.SUBMITTED_FOR_REVIEW))
            .andExpect(jsonPath("$[0].eventLabel").value("Submitted for review"))
            .andExpect(jsonPath("$[0].actorId").value("reviewer1"))
            .andExpect(jsonPath("$[1].eventCode").value(DocumentLifecycleEventCatalog.REVIEW_STARTED))
            .andExpect(jsonPath("$[1].eventLabel").value("Review started"));
    }
}
