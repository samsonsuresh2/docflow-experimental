package com.docflow.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
public class OidcAuthProvider implements AuthProvider {

    private final SecurityProperties properties;
    private volatile NimbusJwtDecoder jwtDecoder;
    private volatile String decoderJwkSetUri;

    public OidcAuthProvider(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public UserContext authenticate(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            throw unauthorized("Missing bearer token");
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw unauthorized("Invalid authorization scheme");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            throw unauthorized("Missing bearer token");
        }

        Jwt jwt = decode(token);
        SecurityProperties.Oidc oidc = properties.getOidc();
        String userId = claimAsString(jwt, oidc.getUserIdClaim());
        if (!StringUtils.hasText(userId)) {
            throw unauthorized("Missing user identity claim");
        }
        return new UserContext(
                userId,
                claimAsString(jwt, oidc.getEmailClaim()),
                Set.of(),
                "OIDC"
        );
    }

    private Jwt decode(String token) {
        try {
            return decoder().decode(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw unauthorized("Invalid bearer token");
        }
    }

    private NimbusJwtDecoder decoder() {
        SecurityProperties.Oidc oidc = properties.getOidc();
        String jwkSetUri = oidc.getJwkSetUri();
        if (!StringUtils.hasText(jwkSetUri)) {
            throw unauthorized("OIDC JWK set URI is not configured");
        }
        NimbusJwtDecoder current = jwtDecoder;
        if (current != null && jwkSetUri.equals(decoderJwkSetUri)) {
            return current;
        }
        synchronized (this) {
            if (jwtDecoder == null || !jwkSetUri.equals(decoderJwkSetUri)) {
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                decoder.setJwtValidator(validator(oidc));
                jwtDecoder = decoder;
                decoderJwkSetUri = jwkSetUri;
            }
            return jwtDecoder;
        }
    }

    private OAuth2TokenValidator<Jwt> validator(SecurityProperties.Oidc oidc) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (StringUtils.hasText(oidc.getIssuerUri())) {
            validators.add(new JwtIssuerValidator(oidc.getIssuerUri()));
        }
        if (StringUtils.hasText(oidc.getAudience())) {
            validators.add(jwt -> hasAudience(jwt.getClaim("aud"), oidc.getAudience())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null)));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private boolean hasAudience(Object claimValue, String expectedAudience) {
        if (claimValue instanceof Collection<?> values) {
            return values.stream()
                    .filter(value -> value != null)
                    .map(String::valueOf)
                    .anyMatch(expectedAudience::equals);
        }
        if (claimValue instanceof String value) {
            return expectedAudience.equals(value);
        }
        return false;
    }

    private String claimAsString(Jwt jwt, String claimName) {
        if (!StringUtils.hasText(claimName)) {
            return null;
        }
        Object value = jwt.getClaim(claimName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
