package com.docflow.security;

import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsWebConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;
    private final Environment environment;

    public CorsWebConfig(SecurityProperties securityProperties, Environment environment) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        SecurityProperties.Cors cors = securityProperties.getCors();
        validateCorsOrigins(cors);
        registry.addMapping("/**")
                .allowedOrigins(toArray(cors.getAllowedOrigins()))
                .allowedMethods(toArray(cors.getAllowedMethods()))
                .allowedHeaders(toArray(cors.getAllowedHeaders()))
                .exposedHeaders(toArray(cors.getExposedHeaders()))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAgeSeconds());
    }

    private void validateCorsOrigins(SecurityProperties.Cors cors) {
        if (!prodLikeProfileActive() || cors.getAllowedOrigins() == null) {
            return;
        }
        boolean wildcardConfigured = cors.getAllowedOrigins().stream()
                .anyMatch(origin -> "*".equals(origin == null ? null : origin.trim()));
        if (wildcardConfigured) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed for UAT/prod profiles");
        }
    }

    private boolean prodLikeProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production") || profile.equals("uat"));
    }

    private String[] toArray(List<String> values) {
        if (values == null) {
            return new String[0];
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
    }
}
