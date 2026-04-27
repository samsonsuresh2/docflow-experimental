package com.docflow.security;

import com.docflow.auth.AuthProperties;
import com.docflow.auth.UserRoleService;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextAuthenticationFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void applicationYamlDoesNotSetDefaultDevProfile() throws Exception {
        String applicationYaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(applicationYaml).doesNotContain("active: dev");
        assertThat(applicationYaml).doesNotContain("spring.profiles.active");
    }

    @Test
    void defaultAuthModeIsDisabled() {
        SecurityProperties properties = new SecurityProperties();

        assertThat(properties.getAuthMode()).isEqualTo(AuthMode.DISABLED);
    }

    @Test
    void disabledAuthReturnsUnauthorizedJson() throws Exception {
        SecurityProperties properties = new SecurityProperties();

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, (request, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"message\":\"Authentication is not configured\"");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void devAuthSuccessPopulatesRequestUserContext() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.DEV_AUTH);
        properties.setDevUserId("local-user");
        properties.setDevUserEmail("local-user@example.test");
        properties.setDevUserRoles(List.of(" maker ", "", "reviewer"));

        RequestUserContext requestUserContext = new RequestUserContext();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        UserContextAuthenticationFilter filter = filter(properties, environment, requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestUser> userSeenByChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, captureUser(requestUserContext, userSeenByChain));

        RequestUser user = userSeenByChain.get();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(user).isNotNull();
        assertThat(user.userId()).isEqualTo("local-user");
        assertThat(user.roles()).containsExactly("MAKER", "REVIEWER");
        assertThat(user.activeRole()).isEqualTo("MAKER");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void devAuthNormalizesRolesToUppercase() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.DEV_AUTH);
        properties.setDevUserId("local-user");
        properties.setDevUserRoles(List.of(" maker ", "Reviewer", " "));

        RequestUserContext requestUserContext = new RequestUserContext();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        UserContextAuthenticationFilter filter = filter(properties, environment, requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestUser> userSeenByChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, captureUser(requestUserContext, userSeenByChain));

        assertThat(userSeenByChain.get().roles()).containsExactly("MAKER", "REVIEWER");
    }

    @Test
    void devAuthDisabledReturnsUnauthorizedJson() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.DEV_AUTH);
        properties.setDevUserId("local-user");

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, (request, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"Unauthorized\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"DEV_AUTH is not enabled\"");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void devAuthWorksWhenExplicitlyConfiguredAndAllowDevAuthEnabled() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.DEV_AUTH);
        properties.setAllowDevAuth(true);
        properties.setDevUserId("configured-dev-user");
        properties.setDevUserRoles(List.of("maker"));

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestUser> userSeenByChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, captureUser(requestUserContext, userSeenByChain));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(userSeenByChain.get().userId()).isEqualTo("configured-dev-user");
    }

    @Test
    void headerAuthSuccessWithValidHeadersPopulatesRequestUserContext() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/search");
        request.addHeader("X-USER-ID", "samson");
        request.addHeader("X-USER-EMAIL", "samson@example.test");
        request.addHeader("X-USER-ROLES", "maker, REVIEWER, ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestUser> userSeenByChain = new AtomicReference<>();

        filter.doFilter(request, response, captureUser(requestUserContext, userSeenByChain));

        RequestUser user = userSeenByChain.get();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(user).isNotNull();
        assertThat(user.userId()).isEqualTo("samson");
        assertThat(user.roles()).containsExactly("MAKER", "REVIEWER");
        assertThat(user.activeRole()).isEqualTo("MAKER");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void headerAuthHeadersAreIgnoredWhenHeaderAuthIsNotExplicitlyConfigured() throws Exception {
        SecurityProperties properties = new SecurityProperties();

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/search");
        request.addHeader("X-USER-ID", "samson");
        request.addHeader("X-USER-ROLES", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"message\":\"Authentication is not configured\"");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }


    @Test
    void headerAuthNormalizesRolesToUppercase() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/search");
        request.addHeader("X-USER-ID", "samson");
        request.addHeader("X-USER-ROLES", " maker , reviewer, ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestUser> userSeenByChain = new AtomicReference<>();

        filter.doFilter(request, response, captureUser(requestUserContext, userSeenByChain));

        assertThat(userSeenByChain.get().roles()).containsExactly("MAKER", "REVIEWER");
    }

    @Test
    void headerAuthMissingUserHeaderReturnsUnauthorizedJson() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, (request, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":\"Unauthorized\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"Missing authentication header: X-USER-ID\"");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void devLoginPathSkipsAuthenticationOnlyForAllowedDevAuthLocalProfile() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.DEV_AUTH);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertSkippedWithoutAuthentication("/api/auth/dev-login", properties, environment);
    }

    @Test
    void devLoginPathSkipsAuthenticationWhenDevAuthExplicitlyAllowed() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.DEV_AUTH);
        properties.setAllowDevAuth(true);

        assertSkippedWithoutAuthentication("/api/auth/dev-login", properties, new MockEnvironment());
    }

    @Test
    void devLoginPathDoesNotSkipAuthenticationForDisabledAuth() throws Exception {
        assertRequiresAuthentication("/api/auth/dev-login", new SecurityProperties(), new MockEnvironment(), "Authentication is not configured");
    }

    @Test
    void devLoginPathDoesNotSkipAuthenticationForHeaderAuth() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        assertRequiresAuthentication("/api/auth/dev-login", properties, new MockEnvironment(), "Missing authentication header: X-USER-ID");
    }

    @Test
    void devLoginPathDoesNotSkipAuthenticationForOidcAuth() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.OIDC_AUTH);
        properties.getOidc().setJwkSetUri("https://issuer.example.test/jwks");
        properties.getOidc().setIssuerUri("https://issuer.example.test");
        properties.getOidc().setAudience("docflow");

        assertRequiresAuthentication("/api/auth/dev-login", properties, new MockEnvironment(), "Missing bearer token");
    }

    @Test
    void actuatorHealthSkipsAuthentication() throws Exception {
        assertSkippedWithoutAuthentication("/actuator/health", new SecurityProperties(), new MockEnvironment());
    }

    @Test
    void actuatorInfoSkipsAuthentication() throws Exception {
        assertSkippedWithoutAuthentication("/actuator/info", new SecurityProperties(), new MockEnvironment());
    }

    @Test
    void otherAuthEndpointsDoNotSkipAuthenticationByDefault() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/auth/me"), response, (request, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void clearsRequestUserContextBeforeProcessingRequest() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        RequestUserContext requestUserContext = new RequestUserContext();
        requestUserContext.setCurrentUser(new RequestUser("stale-user", java.util.Set.of("ADMIN"), "ADMIN"));
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean contextEmptyInChain = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/auth/dev-login"), response, (request, servletResponse) ->
                contextEmptyInChain.set(requestUserContext.getCurrentUser().isEmpty()));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(contextEmptyInChain.get()).isFalse();
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void failedAuthClearsStaleRequestUserContext() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);

        RequestUserContext requestUserContext = new RequestUserContext();
        requestUserContext.setCurrentUser(new RequestUser("stale-user", java.util.Set.of("ADMIN"), "ADMIN"));
        UserContextAuthenticationFilter filter = filter(properties, new MockEnvironment(), requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/documents/search"), response, (request, servletResponse) -> {
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    @Test
    void oidcAuthenticatedUserUsesInternalDocFlowRoles() throws Exception {
        SecurityProperties properties = oidcProperties();
        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filterWithOidcUser(properties, requestUserContext, List.of("MAKER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/search");
        request.addHeader("Authorization", "Bearer token-for-test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestUser> userSeenByChain = new AtomicReference<>();

        filter.doFilter(request, response, captureUser(requestUserContext, userSeenByChain));

        RequestUser user = userSeenByChain.get();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(user.userId()).isEqualTo("oidc-user");
        assertThat(user.roles()).containsExactly("MAKER");
        assertThat(user.activeRole()).isEqualTo("MAKER");
    }

    @Test
    void oidcAuthValidationFailsWhenJwkSetUriMissing() {
        SecurityProperties properties = oidcProperties();
        properties.getOidc().setJwkSetUri(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwk-set-uri");
    }

    @Test
    void oidcAuthValidationFailsWhenIssuerUriMissing() {
        SecurityProperties properties = oidcProperties();
        properties.getOidc().setIssuerUri(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    @Test
    void oidcAuthValidationAllowsMissingAudience() {
        SecurityProperties properties = oidcProperties();
        properties.getOidc().setAudience(null);

        properties.validate();
    }

    private UserContextAuthenticationFilter filter(SecurityProperties properties,
                                                   MockEnvironment environment,
                                                   RequestUserContext requestUserContext) {
        return filter(properties, environment, requestUserContext, List.of("MAKER", "REVIEWER"));
    }

    private UserContextAuthenticationFilter filter(SecurityProperties properties,
                                                   MockEnvironment environment,
                                                   RequestUserContext requestUserContext,
                                                   List<String> internalRoles) {
        DevAuthProvider devAuthProvider = new DevAuthProvider(properties, environment);
        HeaderAuthProvider headerAuthProvider = new HeaderAuthProvider(properties);
        OidcAuthProvider oidcAuthProvider = new OidcAuthProvider(properties);
        AuthenticationResolver authenticationResolver = new AuthenticationResolver(properties, devAuthProvider, headerAuthProvider, oidcAuthProvider);
        UserRoleService userRoleService = mock(UserRoleService.class);
        when(userRoleService.findRolesForUser(anyString(), anyBoolean())).thenReturn(internalRoles);
        return new UserContextAuthenticationFilter(authenticationResolver, requestUserContext, objectMapper, userRoleService, new AuthProperties());
    }

    private UserContextAuthenticationFilter filterWithOidcUser(SecurityProperties properties,
                                                               RequestUserContext requestUserContext,
                                                               List<String> internalRoles) {
        OidcAuthProvider oidcAuthProvider = mock(OidcAuthProvider.class);
        when(oidcAuthProvider.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UserContext("oidc-user", "oidc-user@example.test", java.util.Set.of(), "OIDC"));
        AuthenticationResolver authenticationResolver = new AuthenticationResolver(
                properties,
                new DevAuthProvider(properties, new MockEnvironment()),
                new HeaderAuthProvider(properties),
                oidcAuthProvider
        );
        UserRoleService userRoleService = mock(UserRoleService.class);
        when(userRoleService.findRolesForUser(anyString(), anyBoolean())).thenReturn(internalRoles);
        return new UserContextAuthenticationFilter(authenticationResolver, requestUserContext, objectMapper, userRoleService, new AuthProperties());
    }

    private FilterChain captureUser(RequestUserContext requestUserContext, AtomicReference<RequestUser> userSeenByChain) {
        return (request, response) -> userSeenByChain.set(requestUserContext.requireUser());
    }

    private void assertSkippedWithoutAuthentication(String path, SecurityProperties properties, MockEnvironment environment) throws Exception {
        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, environment, requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("GET", path), response, (request, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    private void assertRequiresAuthentication(String path,
                                              SecurityProperties properties,
                                              MockEnvironment environment,
                                              String expectedMessage) throws Exception {
        RequestUserContext requestUserContext = new RequestUserContext();
        UserContextAuthenticationFilter filter = filter(properties, environment, requestUserContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(new MockHttpServletRequest("POST", path), response, (request, servletResponse) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"message\":\"" + expectedMessage + "\"");
        assertThat(requestUserContext.getCurrentUser()).isEmpty();
    }

    private SecurityProperties oidcProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.OIDC_AUTH);
        properties.getOidc().setJwkSetUri("https://issuer.example.test/jwks");
        properties.getOidc().setIssuerUri("https://issuer.example.test");
        properties.getOidc().setAudience("docflow");
        return properties;
    }
}
