package com.docflow.domain.repository;

import com.docflow.domain.DocumentParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentParent, Long> {

    Optional<DocumentParent> findByDocumentNumber(String documentNumber);
}
