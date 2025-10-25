package com.docflow.service;

import com.docflow.domain.DocumentParent;
import org.springframework.stereotype.Service;

@Service
public class DefaultRuleService implements RuleService {

    @Override
    public boolean validateForClosure(DocumentParent document) {
        return true;
    }
}
