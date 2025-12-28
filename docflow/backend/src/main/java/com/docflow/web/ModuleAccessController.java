package com.docflow.web;

import com.docflow.context.RequestUserContext;
import com.docflow.security.ModuleAccessService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@Validated
public class ModuleAccessController {

    private final ModuleAccessService moduleAccessService;
    private final RequestUserContext requestUserContext;

    public ModuleAccessController(ModuleAccessService moduleAccessService, RequestUserContext requestUserContext) {
        this.moduleAccessService = moduleAccessService;
        this.requestUserContext = requestUserContext;
    }

    @GetMapping("/modules")
    public AllowedModulesResponse getModules() {
        if (requestUserContext.getCurrentUser().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context missing");
        }
        List<String> modules = moduleAccessService.getAllowedModulesForCurrentUser();
        return new AllowedModulesResponse(modules);
    }
}

record AllowedModulesResponse(@NotNull List<String> modules) {
}
