package com.docflow.service;

import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.RoleWorkflowActionAccessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WorkflowPermissionService {

    private final RoleWorkflowActionAccessRepository repository;

    public WorkflowPermissionService(RoleWorkflowActionAccessRepository repository) {
        this.repository = repository;
    }

    public Set<String> getAllowedActions(String activeRole, DocumentStatus status) {
        if (!StringUtils.hasText(activeRole) || status == null) {
            return Set.of();
        }
        List<String> raw = repository.findEnabledActionCodes(activeRole.trim(), status.name());
        Set<String> normalized = new LinkedHashSet<>();
        for (String action : raw) {
            if (action == null || action.isBlank()) {
                continue;
            }
            normalized.add(action.trim().toUpperCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }

    public void assertAllowed(String activeRole, DocumentStatus status, String actionCode) {
        if (!StringUtils.hasText(actionCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing workflow action code");
        }
        Set<String> allowed = getAllowedActions(activeRole, status);
        String normalizedAction = actionCode.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalizedAction)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Role " + (activeRole == null ? "(none)" : activeRole) + " cannot perform action " + normalizedAction + " from status " + status
            );
        }
    }
}
