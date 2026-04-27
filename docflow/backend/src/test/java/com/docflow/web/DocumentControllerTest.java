package com.docflow.web;

import com.docflow.api.dto.DocumentSummary;
import com.docflow.auth.AuthProperties;
import com.docflow.auth.UserRoleService;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.DocumentStatus;
import com.docflow.security.AuthenticationResolver;
import com.docflow.security.ModuleAccessService;
import com.docflow.service.DocumentService;
import com.docflow.service.RelatedEntityService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private RelatedEntityService relatedEntityService;

    @MockBean
    private RequestUserContext requestUserContext;

    @MockBean
    private AuthenticationResolver authenticationResolver;

    @MockBean
    private ModuleAccessService moduleAccessService;

    @MockBean
    private UserRoleService userRoleService;

    @MockBean
    private AuthProperties authProperties;

    @Test
    void rejectsInvalidStatusWithAllowedValues() throws Exception {
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/search")
                .param("status", "SUBMITTED"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(documentService);
    }

    @Test
    void acceptsOpenStatus() throws Exception {
        when(requestUserContext.getCurrentUser()).thenReturn(Optional.empty());
        when(documentService.searchDocuments(any(), any(), any(), any(), anyMap(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        ArgumentCaptor<DocumentStatus> statusCaptor = ArgumentCaptor.forClass(DocumentStatus.class);

        mockMvc.perform(get("/api/documents/search")
                .param("status", "OPEN"))
            .andExpect(status().isOk());

        verify(documentService).searchDocuments(
            isNull(),
            statusCaptor.capture(),
            isNull(),
            isNull(),
            anyMap(),
            any()
        );
        assertThat(statusCaptor.getValue()).isEqualTo(DocumentStatus.OPEN);
    }
}
