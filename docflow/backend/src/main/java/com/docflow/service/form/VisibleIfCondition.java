package com.docflow.service.form;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class VisibleIfCondition {

    private String field;
    private Set<String> notIn = Collections.emptySet();

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Set<String> getNotIn() {
        return notIn;
    }

    public void setNotIn(Set<String> notIn) {
        if (notIn == null) {
            this.notIn = Collections.emptySet();
        } else {
            this.notIn = Collections.unmodifiableSet(new LinkedHashSet<>(notIn));
        }
    }
}
