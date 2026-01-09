package com.docflow.service;

import com.docflow.domain.DocumentStatus;
import com.docflow.domain.repository.RoleWorkflowActionAccessRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowPermissionServiceTest {

    private final RoleWorkflowActionAccessRepository repository = mock(RoleWorkflowActionAccessRepository.class);
    private final WorkflowPermissionService service = new WorkflowPermissionService(repository);

    @Test
    void returnsAllowedActionsForRoleAndStatus() {
        when(repository.findEnabledActionCodes("REVIEWER", "OPEN"))
            .thenReturn(List.of("START_REVIEW", "APPROVE"));

        Set<String> allowed = service.getAllowedActions("REVIEWER", DocumentStatus.OPEN);

        assertThat(allowed).containsExactlyInAnyOrder("START_REVIEW", "APPROVE");
    }

    @Test
    void assertAllowedThrowsForbiddenWhenMissing() {
        when(repository.findEnabledActionCodes("MAKER", "DRAFT"))
            .thenReturn(List.of("SUBMIT"));

        assertThatThrownBy(() -> service.assertAllowed("MAKER", DocumentStatus.DRAFT, "APPROVE"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("cannot perform action APPROVE");
    }
}
