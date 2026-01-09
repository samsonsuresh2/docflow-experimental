package com.docflow.domain;

import com.docflow.domain.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "workflow_actions")
public class WorkflowAction {

    @Id
    @Column(name = "action_code", nullable = false, length = 50)
    private String actionCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "enabled", nullable = false, length = 1)
    @JdbcTypeCode(Types.CHAR)
    @Convert(converter = BooleanToYNConverter.class)
    private Boolean enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowAction)) return false;
        WorkflowAction that = (WorkflowAction) o;
        return Objects.equals(actionCode, that.actionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionCode);
    }
}
