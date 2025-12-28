package com.docflow.auth;

import com.docflow.context.RequestUserContext;
import com.docflow.security.ModuleAccessService;
import com.docflow.security.ModuleCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private static final String SESSION_USER_ID = "AUTHENTICATED_USER_ID";
    private static final String SESSION_ACTIVE_ROLE = "ACTIVE_ROLE";

    private final UserRoleService userRoleService;
    private final AuthProperties authProperties;
    private final RequestUserContext requestUserContext;
    private final ModuleAccessService moduleAccessService;

    public AuthController(UserRoleService userRoleService,
                          AuthProperties authProperties,
                          RequestUserContext requestUserContext,
                          ModuleAccessService moduleAccessService) {
        this.userRoleService = userRoleService;
        this.authProperties = authProperties;
        this.requestUserContext = requestUserContext;
        this.moduleAccessService = moduleAccessService;
    }

    @PostMapping("/dev-login")
    public AuthenticatedResponse devLogin(@Valid @RequestBody DevLoginRequest request, HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_USER_ID, request.userId().trim());
        session.removeAttribute(SESSION_ACTIVE_ROLE);
        return new AuthenticatedResponse(request.userId().trim());
    }

    @GetMapping("/roles")
    public RolesResponse listRoles(HttpServletRequest request) {
        String userId = currentUser(request);
        List<String> roles = userRoleService.findRolesForUser(userId, authProperties.isImplicitMakerEnabled());
        return new RolesResponse(userId, roles);
    }

    @PostMapping("/active-role")
    @ResponseStatus(HttpStatus.OK)
    public ActiveRoleResponse selectActiveRole(@Valid @RequestBody ActiveRoleRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser(httpRequest);
        List<String> allowedRoles = userRoleService.findRolesForUser(userId, authProperties.isImplicitMakerEnabled());
        String desiredRole = normalizeRole(request.role());
        if (allowedRoles.stream().noneMatch(r -> r.equalsIgnoreCase(desiredRole))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requested role is not permitted for this user");
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_ACTIVE_ROLE, desiredRole);
        return new ActiveRoleResponse(desiredRole);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public SessionContextResponse me(HttpServletRequest request) {
        String userId = currentUser(request);
        String activeRole = activeRole(request);
        return new SessionContextResponse(userId, activeRole);
    }

    private String currentUser(HttpServletRequest request) {
        Object sessionUser = request.getSession(false) != null ? request.getSession(false).getAttribute(SESSION_USER_ID) : null;
        if (sessionUser instanceof String userId && !userId.isBlank()) {
            return userId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }

    private String activeRole(HttpServletRequest request) {
        Object role = request.getSession(false) != null ? request.getSession(false).getAttribute(SESSION_ACTIVE_ROLE) : null;
        return role instanceof String r && !r.isBlank() ? r : null;
    }
}

record DevLoginRequest(@NotBlank String userId) {
}

record AuthenticatedResponse(String userId) {
}

record RolesResponse(String userId, List<String> roles) {
}

record ActiveRoleRequest(@NotBlank String role) {
}

record ActiveRoleResponse(String activeRole) {
}

record SessionContextResponse(String userId, String activeRole) {
}
