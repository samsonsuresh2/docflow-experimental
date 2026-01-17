package com.docflow.api.dto;

import java.util.ArrayList;
import java.util.List;

public class FastTrackDecisionSubmitRequest {

    private List<FastTrackDecisionRequest> decisions = new ArrayList<>();

    public List<FastTrackDecisionRequest> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<FastTrackDecisionRequest> decisions) {
        this.decisions = decisions != null ? decisions : new ArrayList<>();
    }
}
