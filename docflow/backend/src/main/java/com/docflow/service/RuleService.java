package com.docflow.service;

import com.docflow.domain.DocumentParent;

public interface RuleService {

    boolean validateForClosure(DocumentParent document);
}
