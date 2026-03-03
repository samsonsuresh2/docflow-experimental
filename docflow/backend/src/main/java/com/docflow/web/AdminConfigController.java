package com.docflow.web;

import com.docflow.api.dto.UploadFieldsRequest;
import com.docflow.api.dto.UploadFieldsResponse;
import com.docflow.api.dto.UploadSchemaStatusResponse;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.service.ConfigService;
import com.docflow.service.UploadSchemaStatusView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final ConfigService configService;
    private final RequestUserContext requestUserContext;

    public AdminConfigController(ConfigService configService, RequestUserContext requestUserContext) {
        this.configService = configService;
        this.requestUserContext = requestUserContext;
    }

    @GetMapping("/upload")
    public ResponseEntity<UploadSchemaStatusResponse> getUploadConfig() {
        UploadSchemaStatusView view = configService.getUploadSchemaStatus();
        return ResponseEntity.ok(new UploadSchemaStatusResponse(
            view.bindingStrategy(),
            view.activeVersion(),
            view.sandboxVersion(),
            view.configJson(),
            view.updatedBy(),
            view.updatedAt()
        ));
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadFieldsResponse> saveUploadConfig(@Valid @RequestBody UploadFieldsRequest request) {
        RequestUser user = requestUserContext.requireUser();
        String configJson = configService.saveSandboxUploadSchema(request.getConfigJson(), user);
        return ResponseEntity.ok(new UploadFieldsResponse(configJson));
    }

    @PostMapping("/upload/promote")
    public ResponseEntity<Void> promoteUploadConfig() {
        RequestUser user = requestUserContext.requireUser();
        configService.promoteSandboxUploadSchema(user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/review-filters")
    public ResponseEntity<UploadFieldsResponse> getReviewFilterConfig() {
        String configJson = configService.getReviewFilterConfig();
        return ResponseEntity.ok(new UploadFieldsResponse(configJson));
    }

    @PostMapping("/review-filters")
    public ResponseEntity<UploadFieldsResponse> saveReviewFilterConfig(@Valid @RequestBody UploadFieldsRequest request) {
        RequestUser user = requestUserContext.requireUser();
        String configJson = configService.upsertReviewFilterConfig(request.getConfigJson(), user);
        return ResponseEntity.ok(new UploadFieldsResponse(configJson));
    }
}
