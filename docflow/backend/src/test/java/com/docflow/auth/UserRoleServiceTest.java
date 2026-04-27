package com.docflow.auth;

import com.docflow.domain.repository.UserRoleMapRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRoleServiceTest {

    private final UserRoleMapRepository repository = mock(UserRoleMapRepository.class);
    private final UserRoleService service = new UserRoleService(repository);

    @Test
    void findRolesForUserAddsImplicitMakerAndOrdersKnownRoles() {
        when(repository.findEnabledRoles("user1")).thenReturn(java.util.Arrays.asList(" admin ", "reviewer", "", null, "custom"));

        assertThat(service.findRolesForUser("user1", true))
            .containsExactly("MAKER", "REVIEWER", "ADMIN", "CUSTOM");
    }

    @Test
    void findRolesForUserCanReturnOnlyExplicitRoles() {
        when(repository.findEnabledRoles("user1")).thenReturn(List.of("approver"));

        assertThat(service.findRolesForUser("user1", false))
            .containsExactly("APPROVER");
    }
}
