package com.docflow.web;

import com.docflow.api.dto.FastTrackApprovalSearchRequest;
import com.docflow.api.dto.FastTrackDecisionSubmitRequest;
import com.docflow.api.dto.FastTrackDecisionSubmitResponse;
import com.docflow.api.dto.DocumentSummary;
import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.security.ModuleAccessService;
import com.docflow.security.ModuleCode;
import com.docflow.service.FastTrackApprovalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/fast-track-approval")
public class FastTrackApprovalController {

    private final FastTrackApprovalService fastTrackApprovalService;
    private final RequestUserContext requestUserContext;
    private final ModuleAccessService moduleAccessService;

    public FastTrackApprovalController(FastTrackApprovalService fastTrackApprovalService,
                                       RequestUserContext requestUserContext,
                                       ModuleAccessService moduleAccessService) {
        this.fastTrackApprovalService = fastTrackApprovalService;
        this.requestUserContext = requestUserContext;
        this.moduleAccessService = moduleAccessService;
    }

    @PostMapping("/search")
    public ResponseEntity<Page<DocumentSummary>> search(@RequestBody FastTrackApprovalSearchRequest request) {
        RequestUser user = requestUserContext.requireUser();
        assertModuleAccess(user);

        FastTrackApprovalSearchRequest safeRequest = request != null ? request : new FastTrackApprovalSearchRequest();
        int page = safeRequest.getPage() != null ? Math.max(0, safeRequest.getPage()) : 0;
        int size = safeRequest.getSize() != null ? Math.max(1, safeRequest.getSize()) : 10;
        String sortBy = resolveSortProperty(safeRequest.getSortBy());
        Sort.Direction direction = parseDirection(safeRequest.getDirection());
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Map<String, Object> filters = safeRequest.getFilters() != null ? safeRequest.getFilters() : Map.of();
        Page<DocumentSummary> results = fastTrackApprovalService.searchDocuments(
            safeRequest.getId(),
            safeRequest.getMetadataKey(),
            safeRequest.getMetadataValue(),
            filters,
            pageable
        );
        return ResponseEntity.ok(results);
    }

    @PostMapping("/submit-decisions")
    public ResponseEntity<FastTrackDecisionSubmitResponse> submitDecisions(
        @RequestBody FastTrackDecisionSubmitRequest request) {
        RequestUser user = requestUserContext.requireUser();
        assertModuleAccess(user);
        FastTrackDecisionSubmitRequest safeRequest = request != null ? request : new FastTrackDecisionSubmitRequest();
        FastTrackDecisionSubmitResponse response = fastTrackApprovalService.submitDecisions(safeRequest.getDecisions(), user);
        return ResponseEntity.ok(response);
    }

    private void assertModuleAccess(RequestUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        boolean hasApproverRole = user.roles().stream()
            .anyMatch(role -> role != null && role.equalsIgnoreCase("APPROVER"));
        if (!hasApproverRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized for fast-track approval");
        }
        boolean hasModule = moduleAccessService.getAllowedModulesForCurrentUser().stream()
            .anyMatch(module -> module != null && module.equalsIgnoreCase(ModuleCode.FAST_TRACK_APPROVAL));
        if (!hasModule) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Module access denied");
        }
    }

    private Sort.Direction parseDirection(String direction) {
        return Sort.Direction.fromOptionalString(direction).orElse(Sort.Direction.ASC);
    }

    private String resolveSortProperty(String sortBy) {
        if (sortBy == null) {
            return "id";
        }
        String normalized = sortBy.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "documentnumber" -> "documentNumber";
            case "createdby" -> "createdBy";
            case "updatedby" -> "updatedBy";
            case "updatedat" -> "updatedAt";
            case "id", "title", "status" -> normalized;
            default -> "id";
        };
    }
}
