package com.docflow.web;

import com.docflow.api.dto.DataInjectorResponse;
import com.docflow.context.RequestUserContext;
import com.docflow.security.SecurityProperties;
import com.docflow.security.UserContextFilter;
import com.docflow.service.DataInjectorService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataInjectorController.class)
@Import({UserContextFilter.class, RequestUserContext.class})
class DataInjectorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataInjectorService dataInjectorService;

    @MockBean
    private com.docflow.domain.repository.UserRoleRepository userRoleRepository;

    @MockBean
    private SecurityProperties securityProperties;

    @Test
    void uploadExcelReturnsOk() throws Exception {
        Mockito.when(userRoleRepository.findByUserId("maker1")).thenReturn(java.util.List.of(new com.docflow.domain.UserRole()));
        Mockito.when(dataInjectorService.uploadExcel(any(), any())).thenReturn(new DataInjectorResponse());
        Mockito.when(securityProperties.resolveAnonymousUser(any())).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents/data-injector/uploadexcel")
                .file(file)
                .header("X-USER-ID", "maker1"))
            .andExpect(status().isOk());
    }
}
