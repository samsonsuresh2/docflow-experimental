package com.docflow.security;

import com.docflow.context.RequestUserContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ModuleAccessWebConfig implements WebMvcConfigurer {

    private final ModuleAccessService moduleAccessService;
    private final RequestUserContext requestUserContext;

    public ModuleAccessWebConfig(ModuleAccessService moduleAccessService, RequestUserContext requestUserContext) {
        this.moduleAccessService = moduleAccessService;
        this.requestUserContext = requestUserContext;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ModuleAccessInterceptor(moduleAccessService, requestUserContext))
                .addPathPatterns("/api/**");
    }
}
