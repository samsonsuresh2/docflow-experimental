package com.docflow.security;

import com.docflow.auth.AuthProperties;
import com.docflow.auth.UserRoleService;
import com.docflow.context.RequestUserContext;
import com.docflow.web.RestExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiHardeningTest {

    @Test
    void securityHeadersArePresentOnNormalApiResponse() throws Exception {
        mvc(securityProperties()).perform(get("/api/probe")
                        .header("X-USER-ID", "samson"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    void securityHeadersArePresentOnUnauthorizedResponse() throws Exception {
        mvc(securityProperties()).perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    void corsPreflightFromConfiguredOriginSucceeds() throws Exception {
        SecurityProperties properties = securityProperties();
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000"));

        CorsConfiguration cors = corsConfiguration(properties);

        assertThat(cors.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        assertThat(cors.checkHttpMethod(HttpMethod.GET)).contains(HttpMethod.GET);
        assertThat(cors.checkHeaders(List.of("Authorization"))).contains("Authorization");
    }

    @Test
    void corsPreflightFromUnconfiguredOriginDoesNotAllowOrigin() throws Exception {
        SecurityProperties properties = securityProperties();
        properties.getCors().setAllowedOrigins(List.of("http://localhost:3000"));

        CorsConfiguration cors = corsConfiguration(properties);

        assertThat(cors.checkOrigin("http://evil.example")).isNull();
    }

    @Test
    void prodLikeProfileRejectsWildcardCorsOrigin() {
        SecurityProperties properties = securityProperties();
        properties.getCors().setAllowedOrigins(List.of("*"));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> corsConfiguration(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard CORS origins");
    }

    @Test
    void genericExceptionResponseDoesNotExposeStackTrace() throws Exception {
        mvc(securityProperties()).perform(get("/api/fail")
                        .header("X-USER-ID", "samson"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void validationBadRequestUsesJsonShape() throws Exception {
        mvc(securityProperties()).perform(post("/api/validate")
                        .header("X-USER-ID", "samson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void authUnauthorizedUsesJsonShape() throws Exception {
        mvc(securityProperties()).perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing authentication header: X-USER-ID"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    private MockMvc mvc(SecurityProperties properties) {
        RequestUserContext requestUserContext = new RequestUserContext();
        HeaderAuthProvider headerAuthProvider = new HeaderAuthProvider(properties);
        DevAuthProvider devAuthProvider = new DevAuthProvider(properties, new StandardEnvironment());
        OidcAuthProvider oidcAuthProvider = new OidcAuthProvider(properties);
        AuthenticationResolver authenticationResolver = new AuthenticationResolver(properties, devAuthProvider, headerAuthProvider, oidcAuthProvider);
        UserContextAuthenticationFilter authFilter = new UserContextAuthenticationFilter(
                authenticationResolver,
                requestUserContext,
                new ObjectMapper(),
                userRoleService(),
                new AuthProperties()
        );
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new RestExceptionHandler())
                .setValidator(validator)
                .addFilters(new SecurityHeadersFilter(), authFilter)
                .build();
    }

    private SecurityProperties securityProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setAuthMode(AuthMode.HEADER_AUTH);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private CorsConfiguration corsConfiguration(SecurityProperties properties) throws Exception {
        return corsConfiguration(properties, new MockEnvironment());
    }

    @SuppressWarnings("unchecked")
    private CorsConfiguration corsConfiguration(SecurityProperties properties, MockEnvironment environment) throws Exception {
        CorsRegistry registry = new CorsRegistry();
        new CorsWebConfig(properties, environment).addCorsMappings(registry);
        Method method = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        method.setAccessible(true);
        Map<String, CorsConfiguration> configurations = (Map<String, CorsConfiguration>) method.invoke(registry);
        return configurations.get("/**");
    }

    private UserRoleService userRoleService() {
        UserRoleService userRoleService = mock(UserRoleService.class);
        when(userRoleService.findRolesForUser(anyString(), anyBoolean())).thenReturn(List.of("MAKER"));
        return userRoleService;
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/api/fail")
        String fail() {
            throw new RuntimeException("java.sql.SQLException: ORA-00942");
        }

        @PostMapping("/api/validate")
        String validate(@Valid @RequestBody ValidateRequest request) {
            return request.name();
        }
    }

    record ValidateRequest(@NotBlank String name) {
    }
}
