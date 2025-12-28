package com.docflow.service.form;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class UploadFieldDefinition {

    private String name;
    private String label;
    private String type;
    private boolean required;
    private boolean readOnly;
    private String placeholder;
    private VisibleIfCondition visibleIf;
    private Set<String> visibleToRoles = Collections.emptySet();
    private Set<String> editableByRoles = Collections.emptySet();
    private Set<String> requiredAtStatuses = Collections.emptySet();
    private boolean lockAfterFilled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public VisibleIfCondition getVisibleIf() {
        return visibleIf;
    }

    public void setVisibleIf(VisibleIfCondition visibleIf) {
        this.visibleIf = visibleIf;
    }

    public Set<String> getVisibleToRoles() {
        return visibleToRoles;
    }

    public void setVisibleToRoles(Set<String> visibleToRoles) {
        if (visibleToRoles == null) {
            this.visibleToRoles = Collections.emptySet();
        } else {
            this.visibleToRoles = Collections.unmodifiableSet(new LinkedHashSet<>(visibleToRoles));
        }
    }

    public Set<String> getEditableByRoles() {
        return editableByRoles;
    }

    public void setEditableByRoles(Set<String> editableByRoles) {
        if (editableByRoles == null) {
            this.editableByRoles = Collections.emptySet();
        } else {
            this.editableByRoles = Collections.unmodifiableSet(new LinkedHashSet<>(editableByRoles));
        }
    }

    public Set<String> getRequiredAtStatuses() {
        return requiredAtStatuses;
    }

    public void setRequiredAtStatuses(Set<String> requiredAtStatuses) {
        if (requiredAtStatuses == null) {
            this.requiredAtStatuses = Collections.emptySet();
        } else {
            this.requiredAtStatuses = Collections.unmodifiableSet(new LinkedHashSet<>(requiredAtStatuses));
        }
    }

    public boolean isLockAfterFilled() {
        return lockAfterFilled;
    }

    public void setLockAfterFilled(boolean lockAfterFilled) {
        this.lockAfterFilled = lockAfterFilled;
    }

    public String getDisplayLabel() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return name;
    }
}
