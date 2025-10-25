package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.domain.AuditLog;
import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.AuditLogRepository;
import com.docflow.domain.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MetadataAuditIntegrationTest {

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
    private MetadataService metadataService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void metadataChangesProduceAuditTrail() {
        RequestUser user = new RequestUser("maker1", Set.of("MAKER"));

        DocumentParent document = new DocumentParent();
        document.setDocumentNumber("DOC-1001");
        document.setTitle("Board Resolution");
        document.setStatus(DocumentStatus.DRAFT);
        document.setCreatedBy(user.userId());
        document.setCreatedAt(OffsetDateTime.now());
        DocumentParent saved = documentRepository.save(document);

        metadataService.persistMetadata(saved, Map.of(
                "loanAmount", 150000,
                "productType", "TERM_LOAN"
        ), user);

        metadataService.persistMetadata(saved, Map.of(
                "loanAmount", 175000,
                "productType", "TERM_LOAN"
        ), user);

        List<AuditLog> auditEntries = auditLogRepository.findByDocumentIdOrderByChangedAtAsc(saved.getId());
        assertThat(auditEntries)
                .hasSize(3)
                .allMatch(entry -> "maker1".equals(entry.getChangedBy()));

        AuditLog updateEntry = auditEntries.get(2);
        assertThat(updateEntry.getFieldKey()).isEqualTo("loanAmount");
        assertThat(updateEntry.getChangeType()).isEqualTo("UPDATED");
        assertThat(updateEntry.getOldValue()).contains("150000");
        assertThat(updateEntry.getNewValue()).contains("175000");
    }
}
