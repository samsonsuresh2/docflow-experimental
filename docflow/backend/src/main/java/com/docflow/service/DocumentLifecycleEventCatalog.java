package com.docflow.service;

import java.util.Map;

public final class DocumentLifecycleEventCatalog {

    public static final String DOC_UPLOADED = "DOC_UPLOADED";
    public static final String SUBMITTED_FOR_REVIEW = "SUBMITTED_FOR_REVIEW";
    public static final String RESUBMITTED = "RESUBMITTED";
    public static final String REVIEW_STARTED = "REVIEW_STARTED";
    public static final String SENT_BACK_TO_MAKER = "SENT_BACK_TO_MAKER";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REVIEW_COMPLETED = "REVIEW_COMPLETED";
    public static final String FAST_TRACK_APPROVED = "FAST_TRACK_APPROVED";
    public static final String FAST_TRACK_ON_HOLD = "FAST_TRACK_ON_HOLD";
    public static final String FAST_TRACK_REJECTED = "FAST_TRACK_REJECTED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";

    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry(DOC_UPLOADED, "Document uploaded"),
        Map.entry(SUBMITTED_FOR_REVIEW, "Submitted for review"),
        Map.entry(RESUBMITTED, "Resubmitted"),
        Map.entry(REVIEW_STARTED, "Review started"),
        Map.entry(SENT_BACK_TO_MAKER, "Sent back to maker"),
        Map.entry(APPROVED, "Approved"),
        Map.entry(REJECTED, "Rejected"),
        Map.entry(REVIEW_COMPLETED, "Review completed"),
        Map.entry(FAST_TRACK_APPROVED, "Fast-track approved"),
        Map.entry(FAST_TRACK_ON_HOLD, "Fast-track on hold"),
        Map.entry(FAST_TRACK_REJECTED, "Fast-track rejected"),
        Map.entry(STATUS_CHANGED, "Status changed")
    );

    private DocumentLifecycleEventCatalog() {
    }

    public static String labelFor(String eventCode) {
        if (eventCode == null) {
            return "Lifecycle event";
        }
        return LABELS.getOrDefault(eventCode, eventCode);
    }
}
