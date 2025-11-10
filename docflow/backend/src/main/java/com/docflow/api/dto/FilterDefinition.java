package com.docflow.api.dto;

import java.util.ArrayList;
import java.util.List;

public class FilterDefinition {

    private String key;
    private String type;
    private String label;
    private FilterSource source;
    private List<String> options = new ArrayList<>();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public FilterSource getSource() {
        return source;
    }

    public void setSource(FilterSource source) {
        this.source = source;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
