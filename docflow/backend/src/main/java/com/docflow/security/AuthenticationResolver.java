package com.docflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthenticationResolver {

    private final SecurityProperties properties;
    private final DevAuthProvider devAuthProvider;
    private final HeaderAuthProvider headerAuthProvider;
    private final OidcAuthProvider oidcAuthProvider;

    public AuthenticationResolver(SecurityProperties properties,
                                  DevAuthProvider devAuthProvider,
                                  HeaderAuthProvider headerAuthProvider,
                                  OidcAuthProvider oidcAuthProvider) {
        this.properties = properties;
        this.devAuthProvider = devAuthProvider;
        this.headerAuthProvider = headerAuthProvider;
        this.oidcAuthProvider = oidcAuthProvider;
    }

    public UserContext authenticate(HttpServletRequest request) {
        AuthMode authMode = properties.getAuthMode();
        if (authMode == AuthMode.DISABLED) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is not configured");
        }
        if (authMode == AuthMode.DEV_AUTH) {
            return devAuthProvider.authenticate(request);
        }
        if (authMode == AuthMode.HEADER_AUTH) {
            return headerAuthProvider.authenticate(request);
        }
        if (authMode == AuthMode.OIDC_AUTH) {
            return oidcAuthProvider.authenticate(request);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is not configured");
    }

    public boolean isDevLoginPublicAllowed() {
        return properties.getAuthMode() == AuthMode.DEV_AUTH && devAuthProvider.isDevAuthAllowed();
    }
}
