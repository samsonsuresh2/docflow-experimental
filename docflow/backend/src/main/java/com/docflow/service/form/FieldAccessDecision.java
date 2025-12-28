package com.docflow.service.form;

public class FieldAccessDecision {

    private final boolean visible;
    private final boolean editable;
    private final boolean requiredNow;
    private final boolean locked;

    public FieldAccessDecision(boolean visible, boolean editable, boolean requiredNow, boolean locked) {
        this.visible = visible;
        this.editable = editable;
        this.requiredNow = requiredNow;
        this.locked = locked;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEditable() {
        return editable;
    }

    public boolean isRequiredNow() {
        return requiredNow;
    }

    public boolean isLocked() {
        return locked;
    }
}
