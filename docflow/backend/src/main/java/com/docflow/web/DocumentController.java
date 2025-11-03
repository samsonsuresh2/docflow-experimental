package com.docflow.web;

import com.docflow.api.dto.*;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.AuditLog;
import com.docflow.service.DocumentFile;
import com.docflow.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final RequestUserContext requestUserContext;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentService documentService,
                              RequestUserContext requestUserContext,
                              ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.requestUserContext = requestUserContext;
        this.objectMapper = objectMapper;
    }

    // ────────────────────────────── UPLOAD ──────────────────────────────
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
        @Valid @RequestPart("metadata") DocumentUploadMetadata metadata,
        @RequestPart(value = "file", required = false) MultipartFile file) {

        RequestUser user = requestUserContext.requireUser();
        DocumentResponse response = documentService.createDocument(metadata, file, user);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── FETCH ──────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        DocumentResponse response = documentService.getDocument(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-number/{documentNumber}")
    public ResponseEntity<DocumentResponse> getDocumentByNumber(@PathVariable String documentNumber) {
        DocumentResponse response = documentService.getDocumentByNumber(documentNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        try {
            DocumentFile documentFile = documentService.getDocumentFile(id);
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(documentFile.getFilename(), StandardCharsets.UTF_8)
                .build();

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(documentFile.getResource());
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // ────────────────────────────── SUBMIT ──────────────────────────────
    @PutMapping("/{id}/submit")
    public ResponseEntity<DocumentResponse> submitDocument(@PathVariable Long id) {
        RequestUser user = requestUserContext.requireUser();
        DocumentResponse response = documentService.submitDocument(id, user);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── STATUS UPDATE ──────────────────────────────
    @PutMapping("/{id}/status")
    public ResponseEntity<DocumentResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateStatusRequest request) {

        RequestUser user = requestUserContext.requireUser();
        DocumentResponse response = documentService.updateStatus(
            id,
            request.getStatus(),
            user,
            "STATUS_UPDATE",
            request.getComment());
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── METADATA UPDATE ──────────────────────────────
    @PutMapping("/{id}/metadata")
    public ResponseEntity<DocumentResponse> updateMetadata(
        @PathVariable Long id,
        @Valid @RequestBody UpdateMetadataRequest request) {

        RequestUser user = requestUserContext.requireUser();
        DocumentResponse response = documentService.updateMetadata(id, request.getMetadata(), user);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── AUDIT ──────────────────────────────
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditEntryResponse>> getAuditTrail(@PathVariable Long id) {
        List<AuditLog> entries = documentService.getAuditTrail(id);
        List<AuditEntryResponse> response = entries.stream()
            .map(this::mapAuditLog)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-number/{documentNumber}/audit")
    public ResponseEntity<List<AuditEntryResponse>> getAuditTrailByNumber(@PathVariable String documentNumber) {
        List<AuditLog> entries = documentService.getAuditTrailByDocumentNumber(documentNumber);
        List<AuditEntryResponse> response = entries.stream()
            .map(this::mapAuditLog)
            .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── APPROVE ──────────────────────────────
    @PutMapping("/{id}/approve")
    public ResponseEntity<DocumentResponse> approve(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) DocumentActionRequest request) {

        RequestUser user = requestUserContext.requireUser();
        String comment = (request != null) ? request.getComment() : null;
        DocumentResponse response = documentService.approve(id, user, comment);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── REJECT ──────────────────────────────
    @PutMapping("/{id}/reject")
    public ResponseEntity<DocumentResponse> reject(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) DocumentActionRequest request) {

        RequestUser user = requestUserContext.requireUser();
        String comment = (request != null) ? request.getComment() : null;
        DocumentResponse response = documentService.reject(id, user, comment);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── REWORK ──────────────────────────────
    @PutMapping("/{id}/rework")
    public ResponseEntity<DocumentResponse> rework(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) DocumentActionRequest request) {

        RequestUser user = requestUserContext.requireUser();
        String comment = (request != null) ? request.getComment() : null;
        DocumentResponse response = documentService.rework(id, user, comment);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── CLOSE ──────────────────────────────
    @PutMapping("/{id}/close")
    public ResponseEntity<DocumentResponse> close(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) DocumentActionRequest request) {

        RequestUser user = requestUserContext.requireUser();
        String comment = (request != null) ? request.getComment() : null;
        DocumentResponse response = documentService.close(id, user, comment);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── UNDER REVIEW ──────────────────────────────
    @PutMapping("/{id}/under-review")
    public ResponseEntity<DocumentResponse> moveToUnderReview(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) DocumentActionRequest request) {

        RequestUser user = requestUserContext.requireUser();
        String comment = (request != null) ? request.getComment() : null;
        DocumentResponse response = documentService.moveToUnderReview(id, user, comment);
        return ResponseEntity.ok(response);
    }

    // ────────────────────────────── MAPPERS ──────────────────────────────
    private AuditEntryResponse mapAuditLog(AuditLog log) {
        AuditEntryResponse response = new AuditEntryResponse();
        response.setFieldKey(log.getFieldKey());
        response.setOldValue(readJsonValue(log.getOldValue()));
        response.setNewValue(readJsonValue(log.getNewValue()));
        response.setChangeType(log.getChangeType());
        response.setChangedBy(log.getChangedBy());
        response.setChangedAt(log.getChangedAt());
        return response;
    }

    private Object readJsonValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawValue, Object.class);
        } catch (IOException ex) {
            return rawValue;
        }
    }
}
