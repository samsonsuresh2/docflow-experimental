package com.docflow.domain.repository;

import com.docflow.domain.DocumentMetadata;
import com.docflow.domain.DocumentParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    List<DocumentMetadata> findByDocument(DocumentParent document);
}
