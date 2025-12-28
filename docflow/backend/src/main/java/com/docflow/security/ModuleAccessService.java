package com.docflow.security;

import com.docflow.context.RequestUserContext;
import com.docflow.domain.repository.RoleModuleAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ModuleAccessService {
    // TODO Phase 2: centralize per-request authorization and integrate SSO/active-role resolution.

    private final RoleModuleAccessRepository repository;
    private final RequestUserContext requestUserContext;

    public ModuleAccessService(RoleModuleAccessRepository repository, RequestUserContext requestUserContext) {
        this.repository = repository;
        this.requestUserContext = requestUserContext;
    }

    public List<String> getAllowedModulesForCurrentUser() {
        return requestUserContext.getCurrentUser()
                .map(user -> getAllowedModulesForRole(user.activeRole() != null ? user.activeRole() : selectPrimaryRole(user.roles())))
                .orElse(List.of());
    }

    public List<String> getAllowedModulesForRole(String role) {
        if (!StringUtils.hasText(role)) {
            return List.of();
        }
        return repository.findEnabledModules(role.trim().toUpperCase(Locale.ROOT)).stream()
                .map(code -> code == null ? null : code.trim().toUpperCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String selectPrimaryRole(Set<String> roles) {
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .findFirst()
                .orElse("");
    }
}
