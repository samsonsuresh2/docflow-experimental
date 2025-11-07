package com.docflow.domain.repository;

import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentParent, Long> {

    Optional<DocumentParent> findByDocumentNumber(String documentNumber);

    @Query("""
        SELECT d FROM DocumentParent d
        WHERE (:documentNumber IS NULL OR LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :documentNumber, '%')))
          AND (:status IS NULL OR d.status = :status)
        """)
    Page<DocumentParent> searchDocuments(
        @Param("documentNumber") String documentNumber,
        @Param("status") DocumentStatus status,
        Pageable pageable
    );
}
