package com.docflow.context;

import java.util.Collections;
import java.util.Set;

public record RequestUser(String userId, Set<String> roles) {

    public RequestUser {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
