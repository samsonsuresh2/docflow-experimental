package com.docflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class HeaderAuthProvider implements AuthProvider {

    private final SecurityProperties properties;

    public HeaderAuthProvider(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public UserContext authenticate(HttpServletRequest request) {
        String userId = request.getHeader(properties.getHeaderUserId());
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication header: " + properties.getHeaderUserId());
        }
        String email = request.getHeader(properties.getHeaderEmail());
        String rolesHeader = request.getHeader(properties.getHeaderRoles());
        return new UserContext(userId.trim(), trimToNull(email), parseRoles(rolesHeader), "HEADER");
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
