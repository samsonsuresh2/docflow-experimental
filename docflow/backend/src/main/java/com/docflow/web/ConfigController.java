package com.docflow.web;

import com.docflow.api.dto.UploadFieldsRequest;
import com.docflow.api.dto.UploadFieldsResponse;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;
    private final RequestUserContext requestUserContext;

    public ConfigController(ConfigService configService, RequestUserContext requestUserContext) {
        this.configService = configService;
        this.requestUserContext = requestUserContext;
    }

    @GetMapping("/upload-fields")
    public ResponseEntity<UploadFieldsResponse> getUploadFields() {
        String configJson = configService.getUploadFieldsConfig();
        return ResponseEntity.ok(new UploadFieldsResponse(configJson));
    }

    @PostMapping("/upload-fields")
    public ResponseEntity<UploadFieldsResponse> upsertUploadFields(@Valid @RequestBody UploadFieldsRequest request) {
        RequestUser user = requestUserContext.requireUser();
        String config = configService.upsertUploadFieldsConfig(request.getConfigJson(), user);
        return ResponseEntity.ok(new UploadFieldsResponse(config));
    }
}
