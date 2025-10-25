package com.docflow.service;

import com.docflow.domain.DocumentParent;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface RuleService {

    @Transactional(readOnly = true)
    boolean validateForClosure(DocumentParent document);
}
