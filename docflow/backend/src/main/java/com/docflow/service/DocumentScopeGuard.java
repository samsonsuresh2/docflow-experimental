package com.docflow.service;

import com.docflow.context.RequestUser;
import com.docflow.context.RequestUserContext;
import com.docflow.domain.DocumentParent;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DocumentScopeGuard {

    public static final String NO_DOCUMENTS_MESSAGE = "No documents found for the given criteria.";

    private DocumentScopeGuard() {
    }

    public static String ownerConstraint(RequestUserContext context) {
        return context.getCurrentUser()
                .filter(DocumentScopeGuard::isMaker)
                .map(RequestUser::userId)
                .orElse(null);
    }

    public static void assertCanAccess(DocumentParent document, RequestUserContext context) {
        String owner = ownerConstraint(context);
        if (owner == null) {
            return;
        }
        if (document == null || document.getCreatedBy() == null || !owner.equalsIgnoreCase(document.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_DOCUMENTS_MESSAGE);
        }
    }

    private static boolean isMaker(RequestUser user) {
        String activeRole = user.activeRole();
        return activeRole != null && activeRole.equalsIgnoreCase("MAKER");
    }
}
