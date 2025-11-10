package com.docflow.api.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataInjectorResponse {

    private int totalRows;
    private int inserted;
    private int updated;
    private int skipped;
    private List<String> ignoredColumns = new ArrayList<>();

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public List<String> getIgnoredColumns() {
        return Collections.unmodifiableList(ignoredColumns);
    }

    public void setIgnoredColumns(List<String> ignoredColumns) {
        this.ignoredColumns = ignoredColumns != null ? new ArrayList<>(ignoredColumns) : new ArrayList<>();
    }
}
