package com.docflow.web;

import com.docflow.api.dto.*;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.service.ConfigService;
import com.docflow.service.schema.SchemaBindingProperties;
import com.docflow.service.schema.UploadSchemaAdminService;
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
    private final UploadSchemaAdminService uploadSchemaAdminService;
    private final SchemaBindingProperties schemaBindingProperties;

    public AdminConfigController(ConfigService configService, RequestUserContext requestUserContext, UploadSchemaAdminService uploadSchemaAdminService, SchemaBindingProperties schemaBindingProperties) {
        this.configService = configService;
        this.requestUserContext = requestUserContext;
        this.uploadSchemaAdminService = uploadSchemaAdminService;
        this.schemaBindingProperties = schemaBindingProperties;
    }

    @GetMapping("/upload")
    public ResponseEntity<UploadFieldsResponse> getUploadConfig() {
        String configJson = configService.getUploadFieldsConfig();
        return ResponseEntity.ok(new UploadFieldsResponse(configJson));
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadFieldsResponse> saveUploadConfig(@Valid @RequestBody UploadFieldsRequest request) {
        RequestUser user = requestUserContext.requireUser();
        String configJson = configService.upsertUploadFieldsConfig(request.getConfigJson(), user);
        return ResponseEntity.ok(new UploadFieldsResponse(configJson));
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

    @GetMapping("/upload-schema")
    public ResponseEntity<SchemaAdminSnapshotResponse> getUploadSchemaSnapshot() {
        UploadSchemaAdminService.UploadSchemaSnapshot snapshot = uploadSchemaAdminService.getCurrentSnapshot();
        return ResponseEntity.ok(new SchemaAdminSnapshotResponse(
            schemaBindingProperties.getBindingStrategy().name(),
            SchemaConfigResponse.from(snapshot.active()),
            SchemaConfigResponse.from(snapshot.sandbox())
        ));
    }

    @PostMapping("/upload-schema/sandbox")
    public ResponseEntity<SchemaConfigResponse> saveSandbox(@Valid @RequestBody SandboxSchemaRequest request) {
        RequestUser user = requestUserContext.requireUser();
        return ResponseEntity.ok(SchemaConfigResponse.from(
            uploadSchemaAdminService.saveSandbox(request.getAdminConfigJson(), request.getValidationSchemaJson(), user)
        ));
    }

    @PostMapping("/upload-schema/promote")
    public ResponseEntity<SchemaConfigResponse> promoteSandboxToActive() {
        RequestUser user = requestUserContext.requireUser();
        return ResponseEntity.ok(SchemaConfigResponse.from(uploadSchemaAdminService.promoteSandboxToActive(user)));
    }

}
