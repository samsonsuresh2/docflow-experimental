package com.docflow.security;

import java.util.Collections;
import java.util.Set;

public record UserContext(String userId, String email, Set<String> roles, String source) {

    public UserContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
    }
}
