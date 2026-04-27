package com.docflow.security;

import com.docflow.auth.AuthProperties;
import com.docflow.auth.UserRoleService;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class UserContextAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserContextAuthenticationFilter.class);
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info"
    );

    private final AuthenticationResolver authenticationResolver;
    private final RequestUserContext requestUserContext;
    private final ObjectMapper objectMapper;
    private final UserRoleService userRoleService;
    private final AuthProperties authProperties;

    public UserContextAuthenticationFilter(AuthenticationResolver authenticationResolver,
                                           RequestUserContext requestUserContext,
                                           ObjectMapper objectMapper,
                                           UserRoleService userRoleService,
                                           AuthProperties authProperties) {
        this.authenticationResolver = authenticationResolver;
        this.requestUserContext = requestUserContext;
        this.objectMapper = objectMapper;
        this.userRoleService = userRoleService;
        this.authProperties = authProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        requestUserContext.clear();

        if (shouldSkipAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserContext userContext;
        try {
            userContext = authenticationResolver.authenticate(request);
        } catch (ResponseStatusException ex) {
            String message = resolveMessage(ex);
            LOGGER.debug("Authentication failed: {}", message);
            writeUnauthorized(response, message);
            return;
        } catch (RuntimeException ex) {
            LOGGER.debug("Authentication failed: {}", ex.getClass().getSimpleName());
            writeUnauthorized(response, "Unauthorized");
            return;
        }

        try {
            requestUserContext.setCurrentUser(toRequestUser(userContext));
            LOGGER.debug("Authentication succeeded for userId={} source={}", userContext.userId(), userContext.source());
            filterChain.doFilter(request, response);
        } finally {
            requestUserContext.clear();
        }
    }

    private RequestUser toRequestUser(UserContext userContext) {
        Set<String> roles = new LinkedHashSet<>(
                userRoleService.findRolesForUser(userContext.userId(), authProperties.isImplicitMakerEnabled())
        );
        String activeRole = roles.stream().findFirst().orElse(null);
        return new RequestUser(userContext.userId(), roles, activeRole);
    }

    private boolean shouldSkipAuthentication(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if ("/api/auth/dev-login".equals(request.getRequestURI())) {
            return authenticationResolver.isDevLoginPublicAllowed();
        }
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "Unauthorized",
                "message", message
        ));
    }

    private String resolveMessage(ResponseStatusException ex) {
        if (ex.getReason() == null || ex.getReason().isBlank()) {
            return "Unauthorized";
        }
        return ex.getReason();
    }
}
