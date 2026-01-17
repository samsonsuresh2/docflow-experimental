package com.docflow.api.dto;

import java.util.ArrayList;
import java.util.List;

public class FastTrackDecisionSubmitResponse {

    private FastTrackDecisionSummary summary = new FastTrackDecisionSummary();
    private List<FastTrackDecisionResult> results = new ArrayList<>();

    public FastTrackDecisionSummary getSummary() {
        return summary;
    }

    public void setSummary(FastTrackDecisionSummary summary) {
        this.summary = summary != null ? summary : new FastTrackDecisionSummary();
    }

    public List<FastTrackDecisionResult> getResults() {
        return results;
    }

    public void setResults(List<FastTrackDecisionResult> results) {
        this.results = results != null ? results : new ArrayList<>();
    }
}
