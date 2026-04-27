package com.docflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class DevAuthProvider implements AuthProvider {

    private final SecurityProperties properties;
    private final Environment environment;

    public DevAuthProvider(SecurityProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public UserContext authenticate(HttpServletRequest request) {
        if (!isDevAuthAllowed()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "DEV_AUTH is not enabled");
        }
        return new UserContext(
                properties.getDevUserId(),
                properties.getDevUserEmail(),
                normalizeRoles(properties.getDevUserRoles()),
                "DEV"
        );
    }

    public boolean isDevAuthAllowed() {
        boolean localProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("local");
        return localProfileActive || properties.isAllowDevAuth();
    }

    private Set<String> normalizeRoles(Iterable<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            normalized.add(role.trim().toUpperCase(Locale.ROOT));
        }
        return normalized;
    }
}
