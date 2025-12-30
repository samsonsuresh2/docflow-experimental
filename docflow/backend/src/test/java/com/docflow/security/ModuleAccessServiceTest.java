package com.docflow.security;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.repository.RoleModuleAccessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleAccessServiceTest {

    @Mock
    private RoleModuleAccessRepository repository;

    @Mock
    private RequestUserContext requestUserContext;

    @InjectMocks
    private ModuleAccessService moduleAccessService;

    @Test
    void returnsEnabledModulesForRoleNormalisedAndDistinct() {
        when(repository.findEnabledModules("MAKER")).thenReturn(List.of("upload", "UPLOAD ", "reports", "REPORTS"));

        List<String> modules = moduleAccessService.getAllowedModulesForRole("maker");

        assertThat(modules).containsExactly("UPLOAD", "REPORTS");
    }

    @Test
    void usesActiveRoleFromRequestContextWhenAvailable() {
        RequestUser requestUser = new RequestUser("samson", Set.of("MAKER", "CHECKER"), "checker");
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.of(requestUser));
        when(repository.findEnabledModules("CHECKER")).thenReturn(List.of("audit"));

        List<String> modules = moduleAccessService.getAllowedModulesForCurrentUser();

        assertThat(modules).containsExactly("AUDIT");
    }
}
