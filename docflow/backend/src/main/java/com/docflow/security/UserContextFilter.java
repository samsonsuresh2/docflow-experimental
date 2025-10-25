package com.docflow.security;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.UserRole;
import com.docflow.domain.repository.UserRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_HEADER = "X-USER-ID";

    private final RequestUserContext requestUserContext;
    private final UserRoleRepository userRoleRepository;

    public UserContextFilter(RequestUserContext requestUserContext, UserRoleRepository userRoleRepository) {
        this.requestUserContext = requestUserContext;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader(USER_HEADER);
        if (userId == null || userId.isBlank()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing X-USER-ID header");
            return;
        }

        Set<String> roles = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleName)
                .collect(Collectors.toUnmodifiableSet());

        if (roles.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "User has no assigned roles");
            return;
        }

        try {
            requestUserContext.setCurrentUser(new RequestUser(userId, roles));
            filterChain.doFilter(request, response);
        } finally {
            requestUserContext.clear();
        }
    }
}
