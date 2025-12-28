package com.docflow.security;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.repository.RoleModuleAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleAccessServiceTest {

    @Mock
    private RoleModuleAccessRepository repository;

    @Mock
    private RequestUserContext userContext;

    @InjectMocks
    private ModuleAccessService service;

    @BeforeEach
    void setup() {
        when(repository.findEnabledModules("ADMIN")).thenReturn(List.of(ModuleCode.REPORT_CONFIG, ModuleCode.AUDIT));
        when(userContext.getCurrentUser()).thenReturn(java.util.Optional.of(new RequestUser("admin1", Set.of("ADMIN"))));
    }

    @Test
    void returnsModulesForRole() {
        List<String> modules = service.getAllowedModulesForRole("admin");
        assertThat(modules).containsExactlyInAnyOrder(ModuleCode.REPORT_CONFIG, ModuleCode.AUDIT);
    }

    @Test
    void returnsModulesForCurrentUser() {
        List<String> modules = service.getAllowedModulesForCurrentUser();
        assertThat(modules).contains(ModuleCode.AUDIT);
    }
}
