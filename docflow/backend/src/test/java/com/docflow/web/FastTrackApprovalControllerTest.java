package com.docflow.web;

import com.docflow.api.dto.FastTrackApprovalSearchRequest;
import com.docflow.api.dto.FastTrackDecisionSubmitRequest;
import com.docflow.api.dto.FastTrackDecisionSubmitResponse;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.security.ModuleAccessService;
import com.docflow.security.ModuleCode;
import com.docflow.service.FastTrackApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FastTrackApprovalControllerTest {

    private FastTrackApprovalService service;
    private RequestUserContext requestUserContext;
    private ModuleAccessService moduleAccessService;
    private FastTrackApprovalController controller;

    @BeforeEach
    void setUp() {
        service = mock(FastTrackApprovalService.class);
        requestUserContext = mock(RequestUserContext.class);
        moduleAccessService = mock(ModuleAccessService.class);
        controller = new FastTrackApprovalController(service, requestUserContext, moduleAccessService);
    }

    @Test
    void searchNormalizesPagingSortingAndDelegatesFilters() {
        allowApprover();
        FastTrackApprovalSearchRequest request = new FastTrackApprovalSearchRequest();
        request.setId("DOC-1");
        request.setMetadataKey("region");
        request.setMetadataValue("west");
        request.setFilters(Map.of("amount", 10));
        request.setPage(-2);
        request.setSize(0);
        request.setSortBy("updatedAt");
        request.setDirection("desc");
        when(service.searchDocuments(any(), any(), any(), anyMap(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.search(request);

        verify(service).searchDocuments(eq("DOC-1"), eq("region"), eq("west"), eq(Map.of("amount", 10)), any(Pageable.class));
    }

    @Test
    void searchUsesSafeDefaultsForNullRequest() {
        allowApprover();
        when(service.searchDocuments(any(), any(), any(), anyMap(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(controller.search(null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void submitDecisionsDelegatesWithSafeRequest() {
        allowApprover();
        FastTrackDecisionSubmitResponse response = new FastTrackDecisionSubmitResponse();
        when(service.submitDecisions(any(), eq(user()))).thenReturn(response);

        assertThat(controller.submitDecisions(null).getBody()).isSameAs(response);
    }

    @Test
    void rejectsMissingUserRoleOrModuleAccess() {
        when(requestUserContext.requireUser()).thenReturn(null);
        assertThatThrownBy(() -> controller.search(null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.UNAUTHORIZED);

        when(requestUserContext.requireUser()).thenReturn(new RequestUser("maker1", Set.of("MAKER"), "MAKER"));
        assertThatThrownBy(() -> controller.search(null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        when(requestUserContext.requireUser()).thenReturn(user());
        when(moduleAccessService.getAllowedModulesForCurrentUser()).thenReturn(List.of());
        assertThatThrownBy(() -> controller.search(null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void allowApprover() {
        when(requestUserContext.requireUser()).thenReturn(user());
        when(moduleAccessService.getAllowedModulesForCurrentUser()).thenReturn(List.of(ModuleCode.FAST_TRACK_APPROVAL));
    }

    private RequestUser user() {
        return new RequestUser("approver1", Set.of("APPROVER"), "APPROVER");
    }
}
