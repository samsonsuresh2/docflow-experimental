package com.docflow.auth;

import com.docflow.context.RequestUserContext;
import com.docflow.security.ModuleAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRoleService userRoleService;

    @MockBean
    private AuthProperties authProperties;

    @MockBean
    private RequestUserContext requestUserContext;

    @MockBean
    private ModuleAccessService moduleAccessService;

    @Test
    void meReturnsSessionContextWithAllowedModules() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("AUTHENTICATED_USER_ID", "samson");
        session.setAttribute("ACTIVE_ROLE", "MAKER");

        when(moduleAccessService.getAllowedModulesForCurrentUser()).thenReturn(List.of("UPLOAD", "REPORTS"));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("samson"))
                .andExpect(jsonPath("$.activeRole").value("MAKER"))
                .andExpect(jsonPath("$.allowedModules[0]").value("UPLOAD"))
                .andExpect(jsonPath("$.allowedModules[1]").value("REPORTS"));
    }
}
