package com.docflow.api.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RelatedEntityResponse {

    private String entityName;
    private String label;
    private List<RelatedEntityColumn> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<RelatedEntityColumn> getColumns() {
        return columns;
    }

    public void setColumns(List<RelatedEntityColumn> columns) {
        this.columns = columns != null ? columns : new ArrayList<>();
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }
}
