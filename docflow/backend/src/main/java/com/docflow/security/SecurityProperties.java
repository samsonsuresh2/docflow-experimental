package com.docflow.security;

import com.docflow.context.RequestUser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "docflow.security")
public class SecurityProperties {

    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private String anonymousUserId;
    private Set<String> anonymousRoles = new LinkedHashSet<>();
    private Set<String> anonymousPaths = new LinkedHashSet<>();

    public Optional<RequestUser> resolveAnonymousUser(String requestPath) {
        if (anonymousUserId == null || anonymousUserId.isBlank()) {
            return Optional.empty();
        }
        if (anonymousPaths.isEmpty()) {
            return Optional.empty();
        }

        boolean matches = anonymousPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
        if (!matches) {
            return Optional.empty();
        }

        Set<String> roles = anonymousRoles.isEmpty() ? Collections.emptySet() : Set.copyOf(anonymousRoles);
        return Optional.of(new RequestUser(anonymousUserId, roles));
    }

    public String getAnonymousUserId() {
        return anonymousUserId;
    }

    public void setAnonymousUserId(String anonymousUserId) {
        this.anonymousUserId = anonymousUserId;
    }

    public Set<String> getAnonymousRoles() {
        return Set.copyOf(anonymousRoles);
    }

    public void setAnonymousRoles(Set<String> anonymousRoles) {
        this.anonymousRoles = anonymousRoles != null ? new LinkedHashSet<>(anonymousRoles) : new LinkedHashSet<>();
    }

    public Set<String> getAnonymousPaths() {
        return Set.copyOf(anonymousPaths);
    }

    public void setAnonymousPaths(Set<String> anonymousPaths) {
        this.anonymousPaths = anonymousPaths != null ? new LinkedHashSet<>(anonymousPaths) : new LinkedHashSet<>();
    }
}
