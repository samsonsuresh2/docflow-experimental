package com.docflow.domain.repository;

import com.docflow.domain.DocumentParent;
import com.docflow.domain.DocumentStatus;
import com.docflow.service.search.DocumentSearchFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DocumentRepositoryCustom {

    Page<DocumentParent> searchDocuments(
        String documentNumber,
        DocumentStatus status,
        List<DocumentSearchFilter> dynamicFilters,
        Pageable pageable
    );
}
