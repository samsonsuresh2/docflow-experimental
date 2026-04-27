package com.docflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "docflow.security")
public class SecurityProperties implements InitializingBean {

    private AuthMode authMode = AuthMode.DISABLED;
    private boolean allowDevAuth = false;
    private String devUserId = "dev-user";
    private String devUserEmail;
    private List<String> devUserRoles = new ArrayList<>();
    private String headerUserId = "X-USER-ID";
    private String headerEmail = "X-USER-EMAIL";
    private String headerRoles = "X-USER-ROLES";
    private Cors cors = new Cors();
    private Oidc oidc = new Oidc();

    public AuthMode getAuthMode() {
        return authMode;
    }

    public void setAuthMode(AuthMode authMode) {
        this.authMode = authMode;
    }

    public boolean isAllowDevAuth() {
        return allowDevAuth;
    }

    public void setAllowDevAuth(boolean allowDevAuth) {
        this.allowDevAuth = allowDevAuth;
    }

    public String getDevUserId() {
        return devUserId;
    }

    public void setDevUserId(String devUserId) {
        this.devUserId = devUserId;
    }

    public String getDevUserEmail() {
        return devUserEmail;
    }

    public void setDevUserEmail(String devUserEmail) {
        this.devUserEmail = devUserEmail;
    }

    public List<String> getDevUserRoles() {
        return devUserRoles;
    }

    public void setDevUserRoles(List<String> devUserRoles) {
        this.devUserRoles = devUserRoles;
    }

    public String getHeaderUserId() {
        return headerUserId;
    }

    public void setHeaderUserId(String headerUserId) {
        this.headerUserId = headerUserId;
    }

    public String getHeaderEmail() {
        return headerEmail;
    }

    public void setHeaderEmail(String headerEmail) {
        this.headerEmail = headerEmail;
    }

    public String getHeaderRoles() {
        return headerRoles;
    }

    public void setHeaderRoles(String headerRoles) {
        this.headerRoles = headerRoles;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public void setOidc(Oidc oidc) {
        this.oidc = oidc;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        if (authMode != AuthMode.OIDC_AUTH) {
            return;
        }
        if (!StringUtils.hasText(oidc.getJwkSetUri())) {
            throw new IllegalStateException("docflow.security.oidc.jwk-set-uri is required when auth-mode is OIDC_AUTH");
        }
        if (!StringUtils.hasText(oidc.getIssuerUri())) {
            throw new IllegalStateException("docflow.security.oidc.issuer-uri is required when auth-mode is OIDC_AUTH");
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("Authorization", "Content-Type", "X-USER-ID", "X-USER-EMAIL", "X-USER-ROLES"));
        private List<String> exposedHeaders = new ArrayList<>();
        private boolean allowCredentials = false;
        private long maxAgeSeconds = 3600;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }

    public static class Oidc {
        private String issuerUri;
        private String jwkSetUri;
        private String audience;
        private String userIdClaim = "sub";
        private String emailClaim = "email";
        private String rolesClaim = "roles";

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getUserIdClaim() {
            return userIdClaim;
        }

        public void setUserIdClaim(String userIdClaim) {
            this.userIdClaim = userIdClaim;
        }

        public String getEmailClaim() {
            return emailClaim;
        }

        public void setEmailClaim(String emailClaim) {
            this.emailClaim = emailClaim;
        }

        public String getRolesClaim() {
            return rolesClaim;
        }

        public void setRolesClaim(String rolesClaim) {
            this.rolesClaim = rolesClaim;
        }
    }
}
