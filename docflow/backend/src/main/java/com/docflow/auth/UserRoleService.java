package com.docflow.auth;

import com.docflow.domain.repository.UserRoleMapRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserRoleService {

    private final UserRoleMapRepository repository;

    public UserRoleService(UserRoleMapRepository repository) {
        this.repository = repository;
    }

    public List<String> findRolesForUser(String userId, boolean implicitMakerEnabled) {
        Set<String> roles = new LinkedHashSet<>();
        if (implicitMakerEnabled) {
            roles.add("MAKER");
        }
        repository.findEnabledRoles(userId).forEach(role -> {
            if (role != null && !role.isBlank()) {
                roles.add(role.trim().toUpperCase(Locale.ROOT));
            }
        });
        return orderRoles(roles);
    }

    private List<String> orderRoles(Set<String> roles) {
        List<String> ordered = new ArrayList<>();
        List<String> priority = List.of("MAKER", "REVIEWER", "APPROVER", "ADMIN");
        for (String p : priority) {
            if (roles.contains(p)) {
                ordered.add(p);
            }
        }
        for (String role : roles) {
            if (!ordered.contains(role)) {
                ordered.add(role);
            }
        }
        return ordered;
    }
}
