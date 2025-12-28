package com.docflow.security;

import com.docflow.context.RequestUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ModuleAccessInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ModuleAccessService accessService;
    private final RequestUserContext userContext;
    private final Map<String, String> guardedPaths;

    public ModuleAccessInterceptor(ModuleAccessService accessService,
                                   RequestUserContext userContext) {
        this.accessService = accessService;
        this.userContext = userContext;
        this.guardedPaths = Map.of(
                "/api/admin/**", ModuleCode.ADMIN,
                "/api/reports/**", ModuleCode.REPORT_CONFIG,
                "/api/documents/**/audit", ModuleCode.AUDIT,
                "/api/documents/by-number/**/audit", ModuleCode.AUDIT
        );
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        @Nullable String module = resolveModule(path, request);
        if (module == null) {
            return true;
        }
        userContext.getCurrentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated"));
        List<String> allowedModules = accessService.getAllowedModulesForCurrentUser();
        if (!allowedModules.contains(module.toUpperCase(Locale.ROOT))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }
        return true;
    }

    @Nullable
    private String resolveModule(String path, HttpServletRequest request) {
        if (path.startsWith("/api/reports/")) {
            // allow execution endpoints
            if ("exec".equalsIgnoreCase(request.getParameter("mode"))) {
                return null;
            }
        }
        if (path.startsWith("/api/auth/") || path.startsWith("/api/me")) {
            return null;
        }
        if (path.contains("/audit")) {
            return ModuleCode.AUDIT;
        }
        for (Map.Entry<String, String> entry : guardedPaths.entrySet()) {
            if (PATH_MATCHER.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
