package com.docflow.security;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.UserRole;
import com.docflow.domain.repository.UserRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserContextFilter.class);
    private static final String USER_HEADER = "X-USER-ID";
    private static final String SESSION_USER_ID = "AUTHENTICATED_USER_ID";
    private static final String SESSION_ACTIVE_ROLE = "ACTIVE_ROLE";

    private final RequestUserContext requestUserContext;
    private final UserRoleRepository userRoleRepository;
    private final boolean devProfileActive;

    public UserContextFilter(
            RequestUserContext requestUserContext,
            UserRoleRepository userRoleRepository,
            Environment environment
    ) {
        this.requestUserContext = requestUserContext;
        this.userRoleRepository = userRoleRepository;
        this.devProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = resolveUserId(request);
        String activeRole = resolveActiveRole(request);
        Set<String> roles = resolveRoles(userId, activeRole);

        try {
            requestUserContext.setCurrentUser(new RequestUser(userId, roles, activeRole));
            filterChain.doFilter(request, response);
        } finally {
            requestUserContext.clear();
        }
    }

    private String resolveUserId(HttpServletRequest request) throws IOException {
        Object sessionUser = request.getSession(false) != null ? request.getSession(false).getAttribute(SESSION_USER_ID) : null;
        if (sessionUser instanceof String sessionUserId && !sessionUserId.isBlank()) {
            return sessionUserId;
        }
        String userId = request.getHeader(USER_HEADER);
        if (userId == null || userId.isBlank()) {
            if (devProfileActive) {
                LOGGER.warn(
                        "Request {} {} missing authentication; continuing because 'dev' profile is active",
                        request.getMethod(),
                        request.getRequestURI()
                );
                return "dev-user";
            }
            throw new IOException("Missing authentication");
        }
        return userId;
    }

    private String resolveActiveRole(HttpServletRequest request) {
        Object sessionRole = request.getSession(false) != null ? request.getSession(false).getAttribute(SESSION_ACTIVE_ROLE) : null;
        if (sessionRole instanceof String role && !role.isBlank()) {
            return role;
        }
        return null;
    }

    private Set<String> resolveRoles(String userId, String activeRole) throws IOException {
        Set<String> roles = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleName)
                .collect(Collectors.toUnmodifiableSet());
        if (activeRole != null) {
            roles = new java.util.HashSet<>(roles);
            roles.add(activeRole);
            roles = Collections.unmodifiableSet(roles);
        }
        if (roles.isEmpty() && !devProfileActive) {
            throw new IOException("User has no assigned roles");
        }
        return roles;
    }
}
